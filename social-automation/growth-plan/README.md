# Growth-plan Telegram notifier

Daily Telegram nudges for the 30-day launch plan (issue #356). Every morning
(09:00 IST) Aumaid (owner) and Zahid (teammate) each get their own task block
for the day; at 15:00 IST both get the day's Done-when checklist. Tickable
lines carry numbered inline buttons — the telegram-webhook edge function
toggles ☐/✅ in the message itself on tap.

| File | Role |
|---|---|
| `tasks.json` | Plan data: `startDate` (= Din 1) + days 0–30, condensed from `docs/GROWTH_PLAN_30DAY.md` |
| `notify.js` | Sender. No dependencies, Node 18+ |
| `../../.github/workflows/growth-plan-notify.yml` | The two cron triggers |

## Editing the plan

Edit `tasks.json`. Each day has `owner[]`, `teammate[]`, `both[]`, `doneWhen[]`
and an optional `weekGoal` (shown on week-start days). `dailyMinimum` entries
carry a `fromDay` so the crash-dashboard line only appears from Din 9 on. To
shift the whole schedule, change `startDate` — it should be a Monday.

The scheduled workflow runs from master, so an edit only takes effect once it
is merged there.

## Testing locally

```
DRY_RUN=1 FORCE_DAY=9 node notify.js            # print, don't send
DRY_RUN=1 FORCE_DAY=17 MODE=evening node notify.js
```

To send for real, set `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` and
`TELEGRAM_TEAMMATE_CHAT_ID` (values in `social-automation/.env`) instead of
`DRY_RUN`.

## Repo secrets

`TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` already exist (IG pipeline).
`TELEGRAM_TEAMMATE_CHAT_ID` is new and must be set before the first run.
