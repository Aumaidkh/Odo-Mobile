# Migrations owed to production

Everything here is written and reviewed. All of it is applied to dev **except
`20260904160000_purchase_credits.sql`**, which is new and still owed to both. None of it has
been applied to production. It sits outside `supabase/migrations/` so that a `supabase db push`
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
