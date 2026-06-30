> The single source of truth for how code moves through Git on **Odo**. Branching, commits, PRs,
tags, and the small set of automations that keep a solo project clean without slowing it down.
Tuned for one developer now, but structured so a second contributor changes nothing.
> 

|  |  |
| --- | --- |
| **Project** | Odo — Your Car's AI Best Friend |
| **Owner** | Founder (solo) |
| **Companion docs** | `Odo_PRD_v1_0.md`, `Odo_ADR_log.md`, `Odo_Build_Plan_and_Roadmap.md` |
| **Status** | Living document — revise when the workflow genuinely changes |
| **Last updated** | June 2026 |

---

## 0. Philosophy

A solo founder doesn't need Git ceremony for its own sake — but the same habits that scale to a team
are the ones that save *you* from yourself at 1 a.m. The rules below optimize for three things:

1. **A readable history** — `git log` should read like a changelog, so future-you (and a future hire) can reconstruct *why*, not just *what*.
2. **A protected `main`** — `main` is always shippable. Nothing half-finished lands there.
3. **Low friction** — short-lived branches, conventional commits, squash-merge. No multi-reviewer gates that a solo dev would just rubber-stamp.

> **Even when working solo, branch and PR.** The PR is your checkpoint to review your own diff, let CI run, and write a clean summary — not bureaucracy.
> 

---

## 1. Repository layout

Single mono-repo (matches the module map in the roadmap):

```
odo/                      # one repo
├─ app/                   # Android entrypoint
├─ shared/               # KMP modules (core:* + feature:*)
├─ functions/            # Supabase Edge Functions (separate deploy unit, same repo)
├─ docs/                 # PRD, ADR log, roadmap, this doc
├─ .github/              # CI workflows, PR template
└─ ...
```

**Why mono-repo:** app, shared KMP code, and Edge Functions change together (e.g. a new
`bill_extraction.v1` field touches the function *and* the client). One repo = atomic cross-cutting
commits and one history. Revisit only if `functions/` ever needs an independent release cadence.

---

## 2. Branching model

A trimmed **GitHub-Flow** (not Git-Flow — Git-Flow's `develop`/`release` overhead is wrong for a
solo MVP).

### 2.1 Long-lived branches

| Branch | Role | Rules |
| --- | --- | --- |
| `main` | Always shippable. Every commit is a state you'd be willing to release. | Protected. No direct pushes. Merge only via PR with green CI. |

That's it for permanent branches at MVP stage. No `develop`. (A `release/*` branch may appear later — see §2.4.)

### 2.2 Short-lived working branches

Every change happens on a branch cut from the latest `main`, merged back via PR, then deleted.

**Naming:** `<type>/<short-kebab-summary>` — optionally prefixed with a tracking id.

```
feat/bill-scanner-camera-capture
fix/odometer-validation-off-by-one
chore/bump-agp-8.5
refactor/extract-healthscore-interface
docs/update-adr-payments
ci/add-lint-workflow
```

If you track work in an issue tracker / the roadmap milestones, prefix with it:

```
M2/feat/bill-scanner-proxy-call
M5/fix/rls-policy-leak
```

| Type | Use for |
| --- | --- |
| `feat` | A new user-facing capability or feature-slice work |
| `fix` | A bug fix |
| `refactor` | Internal change, no behaviour change |
| `chore` | Build, deps, tooling, housekeeping |
| `docs` | Docs only (PRD, ADRs, roadmap, this file) |
| `test` | Adding/altering tests only |
| `ci` | CI/CD pipeline changes |
| `perf` | Performance work |
| `spike` | Throwaway exploration — **never** merged to `main` |

### 2.3 Branch lifecycle rules

- **Cut from fresh `main`:** `git switch main && git pull && git switch -c feat/...`
- **Keep them short-lived:** aim to merge within 1–3 days. Long branches = painful merges.
- **Keep them small & focused:** one branch = one logical change. If you discover unrelated work, branch separately.
- **Rebase, don't merge, to update:** `git fetch && git rebase origin/main` to stay current. Keeps history linear; no "Merge branch 'main' into…" noise.
- **Delete after merge** (local + remote). Squash-merge already preserves the work.

### 2.4 When a `release/*` branch appears (post-MVP)

Only once there are real users on a published build and you need to fix production while `main` has
moved ahead: cut `release/x.y` from the release tag, patch there, tag `vx.y.z`, then cherry-pick the
fix back to `main`. Not needed during MVP (M0–M6) — there's no production to protect yet.

---

## 3. Commit message conventions

**Format: Conventional Commits.** This is non-negotiable
because it makes history machine-readable (changelogs, semver bumps) and human-scannable.

```
<type>(<scope>): <subject>

<body — optional>

<footer — optional>
```

### 3.1 The subject line

- `type` ∈ `feat, fix, refactor, chore, docs, test, ci, perf, build, style` (same vocabulary as branches).
- `scope` = the module or area, lowercase. Use the module names: `billscanner`, `servicelog`, `healthscore`, `fairness`, `reminders`, `paywall`, `core:data`, `core:domain`, `functions`, `app`. Scope is optional but encouraged.
- `subject` = imperative mood, lowercase, **no trailing period**, ≤ 50 chars.
    - ✅ `feat(billscanner): add camera capture and compression`
    - ❌ `Added camera stuff.` / `fixed bug` / `WIP`

> Imperative mood rule of thumb: the subject should complete the sentence *"If applied, this commit will ___"*.
> 

### 3.2 The body (when the *why* isn't obvious)

- Wrap at ~72 chars. Explain **why**, not what (the diff shows what).
- Mention tradeoffs or links to the ADR that justifies the change.

### 3.3 The footer

- Breaking change: `BREAKING CHANGE: <description>` (drives a major version bump).
- References: `Refs: M2`, `Closes #14`, `ADR: ADR-005`.
- **No AI/Anthropic attribution — ever.** Commit messages and PR bodies must **not** contain a
  `Co-Authored-By: Claude …` trailer, a "Generated with Claude Code" line, or any other
  Anthropic/AI sign-off. Every commit is authored solely by the repo's git user.

### 3.4 `feat` vs `fix` and semver

`fix` → patch bump, `feat` → minor bump, `BREAKING CHANGE` → major bump (see §6). Choosing the right
type isn't cosmetic — it drives versioning.

### 3.5 Examples

```
feat(billscanner): extract bill via edge function and prefill log

Calls the ai-bill-scan function and maps bill_extraction.v1 JSON onto
an editable ServiceLogEntry. Low-confidence handwritten bills are
flagged for manual review instead of auto-committed.

Refs: M2
ADR: ADR-005, ADR-006
```

```
fix(core:data): reject backwards odometer readings in sync merge

Last-write-wins was letting a stale offline edit lower the odometer.
Guard added in the merge step; covered by SyncEngineTest.

Refs: M5
```

```
chore(deps): bump AGP to 8.5 across includeBuild boundary
```

```
refactor(healthscore): extract HealthScoreCalculator interface

No behaviour change. Isolates scoring so an ML impl can swap in later
without touching callers.

ADR: ADR-008
```

### 3.6 Anti-patterns (don't)

- `WIP`, `stuff`, `asdf`, `final fix`, `fix fix` — never on `main`. (Fine on a private branch *if* squashed away before merge.)
- One giant commit mixing a feature + refactor + dep bump. Split them.
- Committing secrets, `.env`, API keys, signing keystores — see §7.

---

## 4. Pull Request workflow

Yes, even solo. The PR is your self-review checkpoint.

### 4.1 PR rules

- **Target:** `main`. **Source:** a short-lived branch.
- **Title:** same Conventional-Commit format as a commit subject (it usually *becomes* the squash commit). e.g. `feat(reminders): add insurance expiry scheduling`.
- **Size:** keep diffs reviewable (aim < ~400 lines changed). Large milestone work = multiple PRs.
- **CI must be green** before merge (build + lint + unit tests).
- **Self-review the diff** in the PR view before merging — you catch things in the diff you miss in the editor.

### 4.2 PR description template

Drop this in `.github/pull_request_template.md`:

```markdown
## What
<one-line summary of the change>

## Why
<the problem / motivation — link the milestone or ADR>
Refs: M_  | ADR: ADR-___

## How
<key implementation notes, tradeoffs, anything non-obvious>

## Testing
- [ ] Unit tests added/updated
- [ ] Manually verified on device
- [ ] Offline path checked (if relevant)

## Checklist
- [ ] No secrets / keys / .env committed
- [ ] Module boundaries respected (domain has no framework imports)
- [ ] Conventional-commit title
- [ ] Docs/ADR updated if a decision changed
```

### 4.3 Merge strategy: **Squash and merge** (default)

- One branch collapses to **one clean Conventional-Commit** on `main` → tidy, linear history that doubles as a changelog.
- Edit the squash commit message to be a proper Conventional Commit (don't accept the auto-generated list of "wip" commits).
- **Rebase-merge** only for a small, already-clean series you deliberately want preserved as separate commits.
- **Never use a plain merge commit** on `main` (keeps history linear).
- Delete the branch on merge (enable "automatically delete head branches").

---

## 5. Branch protection & CI gates

Configure on `main` (GitHub → Settings → Branches):

- ☐ Require a pull request before merging.
- ☐ Require status checks to pass (the CI workflow) before merging.
- ☐ Require branches to be up to date before merging.
- ☐ Require linear history (blocks merge commits).
- ☐ Do **not** allow force-pushes or deletions of `main`.
- ☐ (Optional, solo) allow self-approval — keep the PR gate, drop the second-reviewer requirement.

**Minimum CI on every PR** (`.github/workflows/ci.yml`): build the project, run lint/ktlint/detekt,
run unit tests. Add a separate workflow to lint/test `functions/` (Deno) since it's a different runtime.

---

## 6. Tagging & releases (semver)

- **Scheme:** `vMAJOR.MINOR.PATCH` (e.g. `v0.3.1`). Pre-launch the app stays `0.x` — `1.0.0` is the public Play Store launch.
- **Annotated tags only:** `git tag -a v0.3.0 -m "M3: fairness + health score"` then `git push --tags`.
- **Bump rules** (driven by commit types since the last tag):
    - `BREAKING CHANGE` → MAJOR
    - `feat` → MINOR
    - `fix`/`perf` → PATCH
- **Milestone ↔ tag mapping** (suggested, from the roadmap):

| Milestone | Tag | Meaning |
| --- | --- | --- |
| M0 done | `v0.0.1` | Scaffolding builds & deploys |
| M1 done | `v0.1.0` | Onboarding + service log |
| M2 done | `v0.2.0` | Bill scanner live |
| M3 done | `v0.3.0` | Fairness + health score |
| M4 done | `v0.4.0` | Reminders + vault + cost tracker |
| M5 done | `v0.5.0` | Sync + auth + paywall |
| M6 done | `v0.9.0` | Hardened, store-ready (closed testing) |
| Public launch | `v1.0.0` | MVP live on Play Store |

- **Android `versionCode`/`versionName`:** keep `versionName` == the git tag; bump `versionCode` monotonically on every Play upload (CI can derive it from tag + build number).
- A `CHANGELOG.md` can be auto-generated from Conventional Commits between tags — keep it for release notes once there are users.

---

## 7. What must never be committed

Hard rules — a leak here is expensive (ADR-007 exists precisely to keep the Anthropic key off-device).

- **Secrets:** Anthropic API key, Supabase service-role key, Razorpay keys, any token. These live in Edge Function env / CI secrets / a local untracked `.env`, never in Git.
- **Signing material:** release keystore, `keystore.properties`, upload key.
- **Generated/build output:** `/build`, `.gradle/`, `local.properties`, `.iml`, Xcode `DerivedData`, `node_modules/` (functions), `.cxx/`.
- **Large binaries / dumps:** test bill images with real PII, DB dumps.

**Enforcement:**

- A committed `.gitignore` covering the above (Android + KMP + Deno).
- A pre-commit secret scan (e.g. `gitleaks`) — run locally and in CI.
- If a secret *does* land: rotate it immediately (assume compromised), then purge from history (`git filter-repo`) — rotation first, history-scrub second.

---

## 8. Day-to-day cheat sheet

```bash
# start a new piece of work
git switch main && git pull --ff-only
git switch -c M2/feat/bill-scanner-proxy-call

# ... work, committing in small logical chunks ...
git add -p
git commit -m "feat(billscanner): call ai-bill-scan and map response"

# keep current with main (linear history)
git fetch origin
git rebase origin/main

# push and open a PR
git push -u origin HEAD
# -> open PR, fill template, wait for green CI, self-review diff, squash-merge

# after merge
git switch main && git pull --ff-only
git branch -d M2/feat/bill-scanner-proxy-call

# tag a milestone
git tag -a v0.2.0 -m "M2: bill scanner live"
git push --tags
```

---

## 9. Optional automation (add when it pays for itself)

Don't front-load tooling; add each when the manual version starts to hurt.

| Tool | Does | Add when |
| --- | --- | --- |
| `commitlint` + Husky | Rejects non-Conventional commit messages | You catch yourself writing sloppy messages |
| `gitleaks` (pre-commit + CI) | Blocks secret commits | Before the repo ever holds anything near a key — i.e. now |
| `ktlint` / `detekt` in CI | Style + static analysis gate | M0, part of the CI workflow |
| Release-please / semantic-release | Auto CHANGELOG + version bump from commits | Once there are real users (post-`v1.0.0`) |
| Dependabot | Dependency update PRs | Anytime; low cost |

---

*Odo VCS Conventions — keep `main` shippable, history readable, and secrets out. Revise only when the workflow genuinely changes.*
