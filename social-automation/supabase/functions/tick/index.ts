// The one thing pg_cron runs.
//
//   select cron.schedule('social-tick', '*/15 * * * *',
//     $$ select net.http_post(
//          url     := 'https://<ref>.supabase.co/functions/v1/tick',
//          headers := '{"Authorization":"Bearer <anon>","Content-Type":"application/json"}'::jsonb,
//          body    := '{}'::jsonb) $$);
//
// One cron row instead of three, because a schedule the admin panel can edit cannot be three
// hard-coded cron.schedule calls. This wakes up every 15 minutes, asks public.social_schedule
// what is due, and calls `generate` for each slot that is.
//
// It resolves approval once, here, and hands the answer to `generate` — which trusts it rather
// than re-deriving it. The mode and the slot are both needed to answer, and only this function
// has read both.
//
// It also publishes: a row left at `approved` is one the panel's button or auto mode put
// there, and nothing else picks those up. The Telegram button still publishes on the spot.

import { createClient } from "jsr:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

/** How wide a window counts as "now". The cron interval, so no slot is skipped or doubled. */
const WINDOW_MINUTES = 15;

const MINUTES_IN_DAY = 24 * 60;

type Slot = {
  id: string;
  label: string;
  time_of_day: string;
  days_of_week: number[];
  day_of_month: number | null;
  platforms: string[];
  include_story: boolean;
  approval: string;
  enabled: boolean;
  last_fired_at: string | null;
};

/** Local wall-clock parts in `zone`, which is the zone the slots were written in. */
function localNow(zone: string) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: zone,
    hour: "2-digit",
    minute: "2-digit",
    weekday: "short",
    day: "2-digit",
    hour12: false,
  }).formatToParts(new Date());
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
  const isoDay = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].indexOf(get("weekday")) + 1;
  return {
    minutes: Number(get("hour")) * 60 + Number(get("minute")),
    isoDay,
    dayOfMonth: Number(get("day")),
  };
}

/**
 * Whether this slot is due right now.
 *
 * Due is a window rather than an instant: the tick runs on a cron and will never land on
 * 09:00:00 exactly. `last_fired_at` is what stops the same slot firing twice inside it.
 */
function isDue(slot: Slot, now: ReturnType<typeof localNow>): boolean {
  if (!slot.enabled) return false;

  const [h, m] = slot.time_of_day.split(":").map(Number);
  const slotMinutes = h * 60 + m;
  // Wrapped, because the day does. Without the modulo a 23:50 slot is dead: the 23:45 tick
  // gives -5 and the 00:00 tick gives -1430, and no tick in the day is ever inside the window.
  const delta = (now.minutes - slotMinutes + MINUTES_IN_DAY) % MINUTES_IN_DAY;
  if (delta >= WINDOW_MINUTES) return false;

  // Empty means every day; both set means both must match.
  if (slot.days_of_week.length > 0 && !slot.days_of_week.includes(now.isoDay)) return false;
  if (slot.day_of_month !== null && slot.day_of_month !== now.dayOfMonth) return false;

  if (slot.last_fired_at) {
    const sinceMinutes = (Date.now() - new Date(slot.last_fired_at).getTime()) / 60000;
    if (sinceMinutes < WINDOW_MINUTES) return false;
  }
  return true;
}

/**
 * Publish everything sitting at `approved`.
 *
 * That state has two producers and had no consumer: the panel's Approve button, and a slot
 * whose approval is `auto`, whose post the renderer marks approved rather than offering it to
 * anybody. Without this, both were a post that could never go out — and the Telegram button
 * then refused it as already handled.
 *
 * The publishing itself stays in `telegram-webhook`, which holds the Instagram calls and the
 * token refresh. Asking it over HTTP rather than copying that code keeps one publisher; two
 * would drift, and the one that drifted would be the one nobody watched.
 */
async function publishApproved(): Promise<string[]> {
  const { data: rows } = await supabase
    .from("content_queue")
    .select("id")
    .eq("status", "approved")
    .limit(10);

  const done: string[] = [];
  for (const row of rows ?? []) {
    const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/functions/v1/telegram-webhook`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-telegram-bot-api-secret-token": Deno.env.get("TELEGRAM_WEBHOOK_SECRET") ?? "",
      },
      body: JSON.stringify({ publish: row.id }),
    });
    done.push(`${row.id}:${res.status}`);
  }
  return done;
}

Deno.serve(async () => {
  const { data: settings } = await supabase
    .from("social_settings")
    .select("posting_mode, paused, timezone")
    .limit(1)
    .single();

  // The pause is checked first and answers for the whole pipeline. Nothing below it runs.
  if (!settings || settings.paused) {
    return Response.json({ ok: true, skipped: "paused" });
  }
  // `custom` means nothing runs on its own — that is the whole of the mode. A schedule row
  // left behind from a previous mode must not fire under it.
  if (settings.posting_mode === "custom") {
    return Response.json({ ok: true, skipped: "custom" });
  }

  const now = localNow(settings.timezone ?? "Asia/Kolkata");
  const { data: slots } = await supabase
    .from("social_schedule")
    .select("*")
    .eq("enabled", true);

  const due = (slots ?? []).filter((slot: Slot) => isDue(slot, now));
  const fired: string[] = [];

  for (const slot of due) {
    // Stamped before the call, not after. A generate that takes longer than the next tick
    // would otherwise be started twice, and two posts is worse than none.
    const previouslyFiredAt = slot.last_fired_at;
    await supabase
      .from("social_schedule")
      .update({ last_fired_at: new Date().toISOString() })
      .eq("id", slot.id);

    const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/functions/v1/generate`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        story: slot.include_story,
        platforms: slot.platforms,
        slot_id: slot.id,
        // Under `auto` the mode has already answered; otherwise the slot does.
        approval: settings.posting_mode === "auto" ? "auto" : slot.approval,
      }),
    });
    // Rolled back when generate refused. Leaving the stamp marks the slot as having fired and
    // skips it until tomorrow, with a healthy timestamp on the panel and nothing to say why
    // nothing was posted — the failure nobody would go looking for.
    if (!res.ok) {
      await supabase
        .from("social_schedule")
        .update({ last_fired_at: previouslyFiredAt })
        .eq("id", slot.id);
    }
    fired.push(`${slot.label}:${res.status}`);
  }

  const published = await publishApproved();

  return Response.json({ ok: true, checked: (slots ?? []).length, fired, published });
});
