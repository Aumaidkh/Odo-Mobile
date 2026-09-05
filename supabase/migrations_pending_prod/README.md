# Migrations owed to production

Everything here is written, reviewed and **already applied to dev**. None of it has been
applied to production. It sits outside `supabase/migrations/` so that a `supabase db push`
against production cannot apply it by accident — the CLI pushes *every* pending file in
timestamp order, and there is no way to push one.

`supabase/migrations/` therefore mirrors what production actually has. This folder is the
backlog.

## What is in here, and what applying it would do

| Migration | Effect | Safe to apply to prod today? |
|---|---|---|
| `20260822090000_challans.sql` | Creates `challans` | Yes — additive |
| `20260902100000_profile_answers.sql` | Creates `profile_answers` | Yes — additive |
| `20260902160000_backfill_profile_answers_goal.sql` | Copies `profiles.onboarding_goal` into it | Yes — additive, and must run before the drop |
| `20260902170000_drop_profiles_onboarding_goal.sql` | **Drops `profiles.onboarding_goal`** | **No — see below** |
| `20260902180000_resolve_plate.sql` | Creates the `resolve_plate` RPC | Yes — additive |
| `20260904120000_declared_log_source.sql` | Adds the `declared` label to `log_source` | Yes — additive |
| `20260904160000_purchase_credits.sql` | Creates `purchase_claims` + `credit_spends` | Yes — additive |
| `20260905100000_advisory_classifier.sql` | Creates `bill_line_classifications` + `advisory_call_meter` | Yes — additive, and both are deny-all |
| `20260905120000_support_tickets_app.sql` | Adds `client_id` + 5 columns to `support_tickets`; creates `feature_ideas` + `idea_votes` | Yes — every column is added with a default or nullable, and the panel reads `id` as before |
| `20260905130000_support_tickets_app_writes.sql` | Adds the INSERT/UPDATE policies an owner needs, defaults `contact`/`subject`, makes the `client_id` index unconditional | Yes — and required: without it every ticket push is refused `42501`, which the sync treats as permanent |

## The one that is not safe yet

`20260902170000_drop_profiles_onboarding_goal.sql` must wait for a release that no longer
sends the column.

`v1.3.3.3` — the newest tag — still carries it in the profile payload:

```kotlin
@SerialName("onboarding_goal") val onboardingGoal: String? = null,
```

The client sends every field explicitly, nulls included, because an omitted null is a
`PGRST102` on a batch. So dropping the column server-side makes every live installation's
profile push fail with `PGRST204`, and a failed push is silent — the row simply stays
`PENDING` and the owner is told nothing.

This is the same order `docs/QUESTIONNAIRE_PLAN.md` D1 already fixed: backfill and drop ship
in **different releases**, never one. Hold it until a build without that field is live and
adopted.

## Applying to production, when the time comes

```sh
supabase link --project-ref kxxgfhwnidgfvjowqaad
supabase migration list --linked                  # look before touching anything
mv supabase/migrations_pending_prod/<file>.sql supabase/migrations/
supabase db push --linked --dry-run               # confirm it intends only what you moved
supabase db push --linked
```

Move each file back as it is applied, so this folder always answers "what does production
still owe?". Delete the folder when it empties.

## Two things to know first

**A fresh environment now needs both folders.** `supabase/migrations/` alone no longer builds
a database the current app can run against — it has no `profile_answers`. Bootstrapping from
scratch means applying both directories in timestamp order.

**Two files share the version `20260822090000`** — `challans.sql` here, and
`sync_owner_indexes.sql` in `supabase/migrations/`. The ledger records a version, not a
filename, so it can only ever record one of them. That is why `migration list` reports this
version as both matched and missing, and why `db push` asks to "repair" it. Renaming one is
the fix; `feat/workshop-tier` has a commit that does exactly that and has not merged.

## Do not run the repair the CLI suggests

`supabase db push` against **dev** fails with `LegacyDbPushMissingLocalError` and suggests
`migration repair --status reverted` for seven versions dev's ledger has and this tree does
not. Those seven ran — dev has the schema they produced. Marking them reverted tells the CLI
they never did, and the next push would try to run them again.

Dev's ledger and this tree have drifted, and reconciling it is its own piece of work. Nothing
here depends on it: dev already has every schema change this folder describes.

## Applying one of these to dev

`db push` cannot do it — the drift above stops it, and the CLI does not look in this folder
anyway. There is no `supabase db execute`. What works is the Management API's query endpoint,
with the CLI's own token:

```sh
TOKEN=$(security find-generic-password -s "Supabase CLI" -w)   # macOS keychain
curl -sS -X POST "https://api.supabase.com/v1/projects/$(cat ../.temp/project-ref)/database/query" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'User-Agent: SupabaseCLI' \
  --data "$(jq -Rs '{query: .}' < <file>.sql)"
```

Two things that will otherwise waste an hour. **Check the project ref first** — this endpoint
runs whatever you send it, and the ref is the only thing standing between dev and production.
And **send a User-Agent**: without one Cloudflare answers `403 error code: 1010`, which reads
like an auth failure and is not one.

Verify afterwards by asking PostgREST for the table, and ask it for a name you know is absent
in the same run. A 404 alone does not distinguish "missing table" from "broken probe". An anon
read returning `200 []` is **not** evidence that RLS works — a signed-out read gives an empty
array, not a 401 — so read `pg_class.relrowsecurity` if that is the question.
