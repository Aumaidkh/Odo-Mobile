// Sends the day's 30-day-launch-plan tasks to the owner and the teammate on
// Telegram. The plan data lives in tasks.json next to this file; the day number
// comes from tasks.json's startDate (that date is Din 1, the day before it is
// Din 0). Outside the 0..30 range the script exits without sending anything.
//
// Tickable lines start with "☐ " and get one numbered inline button each
// (callback_data gp:<n>). The telegram-webhook edge function toggles the line
// and the button on tap — the ticked state lives in the message itself.
//
// Env:
//   TELEGRAM_BOT_TOKEN          bot token (same bot as the IG approval flow)
//   TELEGRAM_CHAT_ID            owner's (Aumaid's) chat id
//   TELEGRAM_TEAMMATE_CHAT_ID   teammate's (Zahid's) chat id
//   MODE                        "morning" (default) = today's tasks,
//                               "evening" = Done-when checklist reminder
//   RECIPIENTS                  "owner" | "teammate" | "both" (default both)
//   FORCE_DAY                   override the computed day number (testing)
//   DRY_RUN                     "1" = print the messages instead of sending
//
// Run locally: DRY_RUN=1 FORCE_DAY=9 node notify.js
const fs = require("fs");
const path = require("path");

const plan = JSON.parse(fs.readFileSync(path.join(__dirname, "tasks.json"), "utf8"));

const MODE = process.env.MODE === "evening" ? "evening" : "morning";
const RECIPIENTS = process.env.RECIPIENTS || "both";
const DRY_RUN = process.env.DRY_RUN === "1";

// The IST calendar date decides the day number, wherever the runner is.
function istTodayIso() {
  const shifted = new Date(Date.now() + (5 * 60 + 30) * 60 * 1000);
  return shifted.toISOString().slice(0, 10);
}

function currentDayNumber() {
  if (process.env.FORCE_DAY) return Number(process.env.FORCE_DAY);
  const diffMs = Date.parse(istTodayIso()) - Date.parse(plan.startDate);
  return Math.round(diffMs / 86400000) + 1;
}

// Labels carry no weekday — the real date decides it (startDate can shift).
function labelWithDate(day) {
  const d = new Date(Date.parse(plan.startDate) + (day.day - 1) * 86400000);
  const weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const tag = `${weekdays[d.getUTCDay()]}, ${d.getUTCDate()} ${months[d.getUTCMonth()]}`;
  return day.label.replace(`Din ${day.day}`, `Din ${day.day} (${tag})`);
}

function bullets(items) {
  return items.map((t) => `• ${t}`).join("\n");
}

// Numbered checkbox lines; numbering continues across sections of one message.
function checkboxes(items, startAt) {
  return items.map((t, i) => `☐ ${startAt + i}. ${t}`).join("\n");
}

function minimumFor(role, dayNo) {
  const applies = (entry) => dayNo >= (entry.fromDay ?? 1);
  return [...plan.dailyMinimum[role].filter(applies), ...plan.dailyMinimum.both.filter(applies)].map((e) => e.text);
}

function morningMessage(day, role) {
  const name = plan.people[role];
  const own = role === "owner" ? day.owner : day.teammate;
  const parts = [`📋 ${labelWithDate(day)} — ${plan.planTitle}`];
  if (day.weekGoal) parts.push(day.weekGoal);
  if (own.length) parts.push(`${name}, aaj tumhara kaam:\n${checkboxes(own, 1)}`);
  if (day.both.length) {
    parts.push(`Dono milke (${plan.people.owner} + ${plan.people.teammate}):\n${checkboxes(day.both, own.length + 1)}`);
  }
  if (!own.length && !day.both.length) parts.push(`${name}, aaj tumhare liye koi assigned kaam nahi — roz ka minimum bas.`);
  if (day.doneWhen.length) parts.push(`✅ Done-when (shaam tak):\n${bullets(day.doneWhen)}`);
  const minimum = minimumFor(role, day.day);
  if (minimum.length) parts.push(`Roz ka minimum:\n${bullets(minimum)}`);
  const text = parts.join("\n\n");
  return { text, tickCount: own.length + day.both.length };
}

function eveningMessage(day, role) {
  const text = [
    `🌙 ${labelWithDate(day)} — ${plan.people[role]}, Done-when check`,
    checkboxes(day.doneWhen, 1),
    "Jo tick na ho, wahi kal ka pehla kaam. Sheet update karna mat bhoolo.",
  ].join("\n\n");
  return { text, tickCount: day.doneWhen.length };
}

// One "☐ n" button per checkbox line, five per row, matching the webhook's
// rebuild logic exactly.
function keyboard(tickCount) {
  if (!tickCount) return undefined;
  const buttons = Array.from({ length: tickCount }, (_, i) => ({
    text: `☐ ${i + 1}`,
    callback_data: `gp:${i + 1}`,
  }));
  const rows = [];
  for (let i = 0; i < buttons.length; i += 5) rows.push(buttons.slice(i, i + 5));
  return { inline_keyboard: rows };
}

async function send(chatId, { text, tickCount }) {
  if (DRY_RUN) {
    console.log(`--- to ${chatId} (${tickCount} tickable) ---\n${text}\n`);
    return;
  }
  const res = await fetch(`https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      chat_id: chatId,
      text,
      disable_web_page_preview: true,
      ...(keyboard(tickCount) ? { reply_markup: keyboard(tickCount) } : {}),
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok || !body.ok) {
    throw new Error(`sendMessage to ${chatId} failed: HTTP ${res.status} ${JSON.stringify(body).slice(0, 300)}`);
  }
}

async function main() {
  const dayNo = currentDayNumber();
  const day = plan.days.find((d) => d.day === dayNo);
  if (!day) {
    console.log(`Day ${dayNo} is outside the plan (0..30) — nothing to send.`);
    return;
  }

  const chats = {
    owner: process.env.TELEGRAM_CHAT_ID,
    teammate: process.env.TELEGRAM_TEAMMATE_CHAT_ID,
  };
  const roles = RECIPIENTS === "both" ? ["owner", "teammate"] : [RECIPIENTS];
  if (!DRY_RUN && (!process.env.TELEGRAM_BOT_TOKEN || roles.some((r) => !chats[r]))) {
    throw new Error("TELEGRAM_BOT_TOKEN and the chat id for every recipient must be set.");
  }

  for (const role of roles) {
    const message = MODE === "evening" ? eveningMessage(day, role) : morningMessage(day, role);
    await send(chats[role] ?? role, message);
  }
  console.log(`Sent ${MODE} messages for day ${dayNo} to: ${roles.join(", ")}.`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
