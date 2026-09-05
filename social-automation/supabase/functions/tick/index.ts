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
// It decides nothing about approval. That belongs to `generate`, which reads the mode and the
// slot together — two places deciding one thing is how they end up disagreeing.

import { createClient } from "jsr:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

/** How wide a window counts as "now". The cron interval, so no slot is skipped or doubled. */
const WINDOW_MINUTES = 15;

type Slot = {
  id: string;
  label: string;
  time_of_day: string;
  days_of_week: number[];
  day_of_month: number | null;
  platforms: string[];
  variant: string;
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
  const delta = now.minutes - slotMinutes;
  if (delta < 0 || delta >= WINDOW_MINUTES) return false;

  // Empty means every day; both set means both must match.
  if (slot.days_of_week.length > 0 && !slot.days_of_week.includes(now.isoDay)) return false;
  if (slot.day_of_month !== null && slot.day_of_month !== now.dayOfMonth) return false;

  if (slot.last_fired_at) {
    const sinceMinutes = (Date.now() - new Date(slot.last_fired_at).getTime()) / 60000;
    if (sinceMinutes < WINDOW_MINUTES) return false;
  }
  return true;
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
        variant: slot.variant,
        platforms: slot.platforms,
        slot_id: slot.id,
        // Under `auto` the mode has already answered; otherwise the slot does.
        approval: settings.posting_mode === "auto" ? "auto" : slot.approval,
      }),
    });
    fired.push(`${slot.label}:${res.status}`);
  }

  return Response.json({ ok: true, checked: (slots ?? []).length, fired });
});
