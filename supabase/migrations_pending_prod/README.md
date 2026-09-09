# Migrations owed to production

One file left. Everything else that used to sit here has been applied to production and
moved into `supabase/migrations/`, which mirrors what production actually has.

This folder exists so that a `supabase db push` against production cannot apply the
remaining file by accident — the CLI pushes *every* pending file in timestamp order, and
there is no way to push one.

## The one that is not safe yet

`20260902170000_drop_profiles_onboarding_goal.sql` **drops `profiles.onboarding_goal`**. It
must wait for a release that no longer sends the column, and that release is not live yet.

`v1.3.3.3` — the newest tag, and what is on Play — still carries it in the profile payload
(`core/data/.../owner/ProfileRemoteDataSource.kt`):

```kotlin
@SerialName("onboarding_goal") val onboardingGoal: String? = null,
```

The client sends every field explicitly, nulls included, because an omitted null is a
`PGRST102` on a batch. So dropping the column server-side makes every live installation's
profile push fail with `PGRST204`, and a failed push is silent — the row simply stays
`PENDING` and the owner is told nothing.

`releases/v1.4.0` has already dropped the field from the payload. Apply this **after** that
release is live and adopted, not before. The backfill it depends on
(`20260902160000_backfill_profile_answers_goal.sql`) has already run, so the data is safe.

Until then `20260906120000_profiles_goal_compat.sql` holds the seam: the column stays, and a
trigger forwards a goal changed through it into `profile_answers`. Without that an old
client's change lands in a column nothing reads any more. Dropping the column also drops the
trigger, which this file now does — so the two stay in step.

Development has **already** dropped the column, which is why the compatibility migration
checks for it and does nothing when it is absent.

## What was applied, and when

2026-09-06, through the Management API query endpoint, in this order:

| Migration | What it added |
|---|---|
| `20260822090000_challans.sql` | `challans` |
| `20260902100000_profile_answers.sql` | `profile_answers` |
| `20260902160000_backfill_profile_answers_goal.sql` | copied `profiles.onboarding_goal` into it |
| `20260902180000_resolve_plate.sql` | the `resolve_plate` RPC |
| `20260902200000_reference_data.sql` | `labour_rates`, `part_prices`, `job_prices`, `service_schedule` |
| `20260904120000_declared_log_source.sql` | the `declared` label on `log_source` |
| `20260904160000_purchase_credits.sql` | `purchase_claims`, `credit_spends` |
| `20260905120000_support_tickets_app.sql` | `client_id` + 5 columns on `support_tickets`; `feature_ideas`, `idea_votes` |
| `20260905130000_support_tickets_app_writes.sql` | the INSERT/UPDATE policies a ticket push needs |

`20260905100000_advisory_classifier.sql` was already on production before this.

The ledger was then filled in to match, so `migration list` and `db push` see what is really
there. The duplicate-version clash the old version of this file described is gone:
`sync_owner_indexes` is now `20260822091000` and `challans` owns `20260822090000`.

## Applying one of these to production

`db push` cannot do it — dev's ledger and this tree have drifted, and the CLI does not look
in this folder anyway. There is no `supabase db execute`. What works is the Management API's
query endpoint, with the CLI's own token:

```sh
TOKEN=$(security find-generic-password -s "Supabase CLI" -w)   # macOS keychain
curl -sS -X POST "https://api.supabase.com/v1/projects/<ref>/database/query" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'User-Agent: SupabaseCLI' \
  --data "$(jq -Rs '{query: .}' < <file>.sql)"
```

Three things that will otherwise waste an hour. **Check the project ref first** — this endpoint
runs whatever you send it, and the ref is the only thing standing between dev and production;
`local.properties` `supabase.url` is what production's ref must match. **Send a User-Agent**:
without one Cloudflare answers `403 error code: 1010`, which reads like an auth failure and is
not one. And **do not run the repair the CLI suggests** — `migration repair --status reverted`
tells the ledger that migrations which really ran never did, and the next push would run them
again.

Verify afterwards by asking for the object itself, and ask for a name you know is absent in the
same run: a 404 alone does not distinguish "missing" from "broken probe". For a function, a
`401` against a `404` control is what says it deployed. An anon read returning `200 []` is
**not** evidence that RLS works — a signed-out read gives an empty array, not a 401 — so read
`pg_class.relrowsecurity` if that is the question.
