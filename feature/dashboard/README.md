# :feature:dashboard

The app's main surface: a 5-tab shell (Home · Timeline · Scan · Costs · Garage).

## The one rule that governs this module

**Dashboard is an *aggregator* feature. It NEVER imports another `:feature:*`.**

A `:feature:*` module may only depend on `:core:*`. The whole point of the
dashboard is to show data owned by many features — so the temptation to
`implementation(projects.feature.healthscore)` is real and it is **wrong**. It
would invert the dependency graph (the aggregator becomes the most-depended-on
module), couple build times, and break the golden rule in `CLAUDE.md`.

Cross-feature data flows through **`:core:domain` read-model ports**, never
through a sibling feature.

## Shell vs. tab content — two different things

Do not think of "the dashboard" as one thing. It is two:

1. **Shell / chrome** — bottom bar, 5 tab slots, the Scan FAB. Pure
   coordination, zero business logic. Lives in `:app` (composition root) using
   scaffold primitives from `:core:designsystem` / `:core:navigation`. `:app`
   is *allowed* to know every feature — that is its job.
2. **Each tab's content** — owned by the feature that owns that data.

The shell hosts tabs via the Nav3 `entryProvider` registration; the Scan FAB is
a `NavigationManager` command to the bill-scanner route. Neither imports a
feature module directly.

## Tab ownership

| Tab | Kind | Owner | How the shell gets it |
| --- | --- | --- | --- |
| **Home** | Aggregation | `:feature:dashboard` | This module's aggregation use case reads `:core:domain` read-model ports (health score, cost summary, reminders…) and composes a single overview. |
| **Timeline** | Aggregation | `:feature:timeline` | Unified activity feed (services · documents · health · milestones). Reads via `:core:domain`; an entry's detail reuses `ServiceLog.Detail`, sharing reuses `ServiceLog.Share`. |
| **Scan** | Nav action | `:feature:billscanner` | FAB → `NavigationManager.navigate(BillScannerRoute)`. No import. |
| **Costs** | Single-owner | `:feature:cost-tracker` | cost-tracker registers its entry screen via `entryProvider`. |
| **Garage** | Aggregation | `:feature:garage` | Its own module (car home-base grew past a placeholder): car card + documents overview + inline service history, reading car / document / service-log data via `:core:domain` ports. Registers `OdoDestination.Garage` itself. |

**Home is the cross-feature aggregation that lives here** and reads domain ports.
**Timeline · Costs · Garage are owned by their features** (`:feature:servicelog` ·
`:feature:cost-tracker` · `:feature:garage`) and merely hosted by the shell.

## Aggregation pattern (how Home/Garage read cross-feature data)

Each feature exposes a summary/read-model port in `:core:domain`, implemented in
`:core:data` (SQLDelight = source of truth). The dashboard composes them:

```
// :core:domain — the shared contract
interface HealthScoreRepository { fun observe(carId: CarId): Flow<HealthScore> }
interface CostSummaryRepository { fun observe(carId: CarId): Flow<CostSummary> }
interface ReminderRepository    { fun upcoming(carId: CarId): Flow<List<Reminder>> }

// :feature:dashboard — its OWN use case against the shared ports
internal class LoadDashboardOverview(
    private val health: HealthScoreRepository,
    private val costs: CostSummaryRepository,
    private val reminders: ReminderRepository,
) { /* combine() into a DashboardOverview */ }
```

Per `CLAUDE.md`: *"When two features need the same use case, each writes its own
against the shared `:core:domain` port."* The health-score feature computes and
persists the score; the dashboard just reads the port. No coupling.

## Why not a `:feature:x:api` / `:impl` split?

Some large apps expose a thin `api` module per feature so aggregators depend on
`api` without `impl`. At this app's scale that is overkill — the `:core:domain`
ports already serve exactly that role. Do not add it.
