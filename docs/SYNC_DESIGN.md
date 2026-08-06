# Odo Sync Engine — Design

> **Status: design only.** Nothing in this document is implemented yet. The seam
> interfaces (`Syncable`, `Synchronizer`, `SyncEngine`, `SyncScheduler`,
> `SyncStatusProvider`) exist in code as contracts with no implementations; the engine
> itself lands in **M5** ([`ROADMAP.md`](ROADMAP.md#m5--sync-auth-paywall--entitlements-w7)).
>
> This document is the **authoritative** expansion of [`TDD.md`](TDD.md) §8. Where the TDD
> sketch and this file differ, this file wins. `DB_SCHEMA.md` remains authoritative for the
> *server* schema; this file owns the **client** sync columns and local sync state.

---

## 1. Why this document exists

Odo is offline-first: the local SQLDelight DB is the source of truth, and every core
flow (add a car, log a service, scan a bill) must work in a basement parking garage with
no signal. That makes sync a first-class subsystem, not a nice-to-have — and one that is
very easy to get subtly wrong (duplicated rows after reinstall, silently lost offline
edits, a bill row pointing at an image that never uploaded).

The design below is deliberately conservative: **idempotent, resumable, and lossless by
default.** When in doubt, a sync run does nothing rather than something clever.

---

## 2. Principles

1. **Local DB is the source of truth.** The UI reads only from SQLDelight `Flow`s. The
   network never reaches a ViewModel; it only feeds the local DB.
2. **Repositories are the only writers.** Domain code has no idea sync exists — it calls
   `CarRepository.add(...)` and a `PENDING` row appears. `:core:domain` never imports a
   sync type.
3. **Every sync operation is idempotent.** Upsert by client-generated UUID. Running a sync
   twice must be indistinguishable from running it once.
4. **Never lose an offline write silently.** A failed push leaves the row `PENDING` and
   retries forever. If a remote value overwrites a local edit, that is logged as a
   resolved conflict, not dropped quietly.
5. **Resumable, never all-or-nothing.** Progress is committed per entity via cursors, so a
   sync killed mid-run picks up where it stopped instead of restarting.

---

## 3. What we take from Now in Android, and what we must add

Now in Android (NiA) is the reference offline-first Android app, and its *structure* is
worth copying almost verbatim. But NiA's sync is **one-way: server → client**. NiA has no
auth and no user-authored server data — bookmarks and followed topics live only in local
Proto DataStore and are never uploaded. So NiA has no outbox, no push, no conflict
resolution.

Odo is the opposite: nearly everything that matters is user-authored (cars, service logs,
bills, documents) and must go *up*. NiA gives us half the problem.

| Concern | NiA | Odo |
| --- | --- | --- |
| Local DB as SSoT, `OfflineFirst*Repository` | ✅ | adopt as-is |
| `Syncable` / `Synchronizer` seam | ✅ | adopt as-is |
| Per-entity delta cursor | change-list endpoint + version int | Supabase `updated_at > cursor` — no custom endpoint needed |
| Deletes propagate | server `isDelete` flag | `deleted_at` soft delete (DB_SCHEMA §2.3) — same mechanic |
| `isSyncing` status flow for the UI | ✅ `SyncManager` | adopt, but as a `:core:domain` port |
| Retry/backoff via worker result | ✅ | adopt |
| **Pushing local writes** | ❌ none | outbox: `PENDING` rows upserted by id |
| **Conflict resolution** | ❌ none | last-write-wins by `updated_at`; `CONFLICT` reserved for Phase 2 |
| **Blob upload ordering** | ❌ none | image → Storage first, then the row that references it |
| **FK ordering across tables** | ❌ (flat entities) | strict topological order (§8) |
| **Auth / RLS / `owner_id`** | ❌ no auth | server-stamped `owner_id`; local data adopted at sign-in (§9) |
| **Multiplatform** | Android-only (WorkManager everywhere) | engine in `commonMain`; only *scheduling* is `expect`/`actual` |

---

## 4. Local schema contract

### 4.1 Sync columns on every syncable table

Every local table that mirrors a server table **must** carry this exact column set. This is
not optional and not per-table judgement — a table missing one of these cannot be synced,
and adding it later costs a migration.

```sql
created_at     TEXT NOT NULL,              -- ISO-8601, client-stamped on insert
updated_at     TEXT NOT NULL,              -- ISO-8601, client-stamped on every local mutation
deleted_at     TEXT,                       -- soft delete; NULL = live
remote_version TEXT,                       -- server updated_at last seen; NULL = never pushed
sync_status    TEXT NOT NULL DEFAULT 'PENDING'
```

`sync_status` ∈ `PENDING | SYNCED | CONFLICT`:

| Value | Meaning |
| --- | --- |
| `PENDING` | Local mutation not yet accepted by the server. The outbox picks it up. Also the state of a row whose push failed — retry is just the next run. |
| `SYNCED` | Server has this exact version; `remote_version` holds the server's `updated_at`. |
| `CONFLICT` | Reserved for Phase 2 multi-device. **The MVP never writes it** (LWW resolves everything, §7). It exists so the column doesn't need a migration later. |

> There is deliberately **no `FAILED` state.** A failed push is indistinguishable from a
> pending one — it needs exactly the same treatment (retry) — and a separate state only
> creates a way for a row to get stuck outside the outbox. Retry pacing is the scheduler's
> job (§10), not a row's.

Timestamps are ISO-8601 strings (`kotlin.time.Instant.toString()`), matching how
`CarRepositoryImpl` already writes them. **Use `kotlin.time.Instant` / `kotlin.time.Clock`,
never `kotlinx.datetime`** for instants — `kotlinx.datetime` breaks the iOS native link on
Kotlin 2.4. (`kotlinx.datetime.LocalDate` for calendar dates is fine and already in use.)

### 4.2 Local sync state

Cursors live in their own SQLDelight table rather than DataStore — one storage engine, and
cursor updates can share a transaction with the rows they describe (which is what makes a
partially-applied pull safe).

```sql
CREATE TABLE sync_state (
    entity         TEXT NOT NULL PRIMARY KEY,  -- SyncEntity.name
    last_pulled_at TEXT,                       -- server updated_at high-water mark
    last_pushed_at TEXT,                       -- diagnostics only
    last_error     TEXT                        -- last failure message, for the debug screen
);
```

> NiA keeps its change-list versions in Proto DataStore. We don't, because our cursor must
> commit atomically with the rows it covers, and DataStore can't join a SQLDelight
> transaction.

---

## 5. The seam

Five contracts. Everything else is an implementation detail behind them.

```kotlin
// :core:sync — one syncable table/aggregate.
interface Syncable {
    val entity: SyncEntity
    suspend fun syncWith(synchronizer: Synchronizer): Boolean   // false = retry the whole run
}

// :core:sync — what a Syncable is allowed to ask the engine for.
interface Synchronizer {
    suspend fun cursor(entity: SyncEntity): SyncCursor
    suspend fun updateCursor(entity: SyncEntity, update: SyncCursor.() -> SyncCursor)
    suspend fun recordFailure(entity: SyncEntity, cause: Throwable)
}

// :core:sync — orders the Syncables and runs them. The only thing the scheduler calls.
interface SyncEngine {
    suspend fun sync(): SyncResult
}

// :core:sync (actual impls per platform) — "run a sync soon, subject to constraints".
interface SyncScheduler {
    fun scheduleStartupSync()
    fun requestSync(reason: SyncReason)
}

// :core:domain — what the UI is allowed to know. A port, like SessionStatusProvider.
interface SyncStatusProvider {
    val isSyncing: Flow<Boolean>
    val pendingCount: Flow<Int>
    val lastSyncedAt: Flow<Instant?>
}
```

`Syncable` returning `Boolean` (rather than throwing) is NiA's shape and it is the right
one: one entity failing means the *run* retries, and WorkManager's backoff decides when —
no bespoke retry loop inside a repository.

Each `OfflineFirst*Repository` implements `Syncable` for its own table. The engine holds
them in a topologically ordered list (§8) and runs them **sequentially** — not
`awaitAll` like NiA, because our FK ordering is a real constraint and NiA's entities are
independent.

`SyncStatusProvider` lives in `:core:domain` so a feature can render "Syncing…" or a
"3 changes not backed up" chip without depending on `:core:data` — the same Ports &
Adapters trick already used for `SessionStatusProvider` and `CurrentOwnerProvider`.

### 5.1 Why the contracts live in their own module

`:core:sync` holds the seam and the engine; `:core:data` **depends on it**, never the
reverse. That direction is forced, not stylistic: a `Syncable` is implemented by the
repository that owns the table (only it knows the row↔DTO mapping), and a repository wants
to request a sync after a local write. An engine that instead reached *into* `:core:data`
to sync its tables would need `:core:data` while `:core:data` needed the engine — a cycle
Gradle rejects. Inverting it means the engine only ever receives the `Syncable`s Koin hands
it (`getAll<Syncable>()`), and knows nothing about SQLDelight.

```
              :core:sync            contracts + engine. No DB, no network, no Android.
               ▲        ▲
               │        │
         :core:data   <platform worker module>
   (repos implement       (WorkManager worker + SyncScheduler actual, Android-only)
    Syncable)
```

Two things stay out of `:core:sync` on purpose:

- **`SyncStatus`** (`PENDING | SYNCED | CONFLICT`) lives in `:core:data` — it is the
  vocabulary of a database column, not a concept the engine needs.
- **`SyncStatusProvider`** lives in `:core:domain`, because features may depend on the
  domain and must not depend on either `:core:data` or `:core:sync`.

Now in Android keeps its equivalents in `core:data` and splits out only the worker
(`sync:work`). Odo splits one step earlier, because `:core:data` here is a shared
dependency of every feature and shouldn't grow a Supabase/WorkManager surface just to
carry the engine.

---

## 6. Algorithms

Each `Syncable.syncWith` is **push, then pull**, in that order. Pushing first means the
pull's LWW comparison sees our newest version and can't resurrect a stale server row over
a local edit.

### 6.1 Push (outbox)

```
rows = SELECT * FROM <table> WHERE sync_status = 'PENDING' ORDER BY updated_at ASC
for each batch of N rows:
    for rows carrying a blob not yet uploaded:        # §7
        upload to Storage at a deterministic path; set storage_path locally
    response = supabase.from(<table>).upsert(rows.map(::toDto)).select("id,updated_at")
    in one transaction:
        for each accepted row:
            sync_status    = 'SYNCED'
            remote_version = response.updated_at
            # NOTE: do NOT overwrite the local row from the response — the local copy is
            # already the newest version. Only the sync bookkeeping changes.
        sync_state.last_pushed_at = now()
```

Guarantees:

- **Idempotent** — upsert keyed on the client-generated UUID, so a retry after a lost
  response updates the same row instead of creating a twin.
- **Lossless** — a row whose push fails is simply not marked `SYNCED`, so the next run
  picks it up again. There is no path where a `PENDING` row is forgotten.
- **Deletes are pushes.** A soft delete is an ordinary update (`deleted_at` set,
  `sync_status = 'PENDING'`). No tombstone table.

#### 6.1.1 A refusal the server will repeat

A push can fail two ways, and they need opposite reactions:

| Refusal | Examples | Reaction |
| --- | --- | --- |
| **Transient** | 5xx, timeout, dropped connection, `401` (token just expired), `408`, `429` | Leave the rows `PENDING`, stop the entity, let the scheduler's backoff retry. |
| **Permanent** | any other 4xx — a duplicate key, a value the schema rejects, an RLS policy that says no | Take the offending rows out of the outbox: `sync_status = 'CONFLICT'`. **The run carries on.** |

Retrying a permanent refusal cannot change the answer, so leaving those rows `PENDING`
means every later run dies on the same row — and because a failing entity stops the run
(§8), one bad row takes every table after it offline too. That is how a single duplicate
car stops service logs, documents and health scores from ever syncing.

PostgREST answers a batch on its first violation, so a rejected batch says nothing about
the rows after the bad one. A batch of more than one row is therefore re-sent **one row at
a time**: the good rows sync as usual, and only rows refused on their own are marked
`CONFLICT`. A row that fails transiently during that pass stops the run in the usual way.

A `CONFLICT` row keeps its data and still counts as "not backed up". It returns to
`PENDING` the moment the owner edits it.

#### 6.1.2 Adopting the server's identity for a re-added row

Some tables are constrained on something other than the primary key. `cars` allows one live
row per `(owner_id, registration_number)`. A reinstall takes the local database with it, so
onboarding the same car again mints a **fresh UUID for a plate the server already holds** —
and the push, an upsert on the primary key, becomes an INSERT that breaks that rule. Left
alone it is a permanent refusal, i.e. §6.1.1 forever.

So before the push, a table may reconcile its own rows against the server:

```
candidates = PENDING rows with remote_version IS NULL and a plate       # never synced
if candidates is empty: skip entirely — no request is made
server = fetch all live rows for this owner
for each candidate matching a server row by plate, with a different id:
    in one transaction:
        repoint every child table's car_id from the local id to the server id
        rewrite the local row's id to the server id, and take the server's created_at
```

The push that follows is then an update of the row that is already there. The **local copy
still wins on content** — the owner typed it just now, which is newer than whatever the
server has. Only the identity is adopted.

This costs nothing in the normal case: a row that has synced once carries a
`remote_version`, so there are no candidates and the server is never asked.

#### 6.1.3 Reclaiming the primary flag

`cars` also allows one live `is_primary` row per owner, and plate adoption cannot catch
this pair when the plates differ: a reinstall onboards its car as primary with a plate the
server has never seen, while the server still holds the old primary. The push is then a
permanent refusal (`uq_cars_one_primary`), and because every service log and document is
stamped from its car by a server trigger, everything on the car is refused with it.

So a push whose rows include a live primary car first clears `is_primary` on the owner's
other cars, in one filtered `PATCH`, and then pushes. **The device wins on purpose** — the
local flag is the owner's most recent word on which car is primary, the same tie-break §7
uses. The demoted rows get a fresh `updated_at` from the server's trigger, so every device
pulls the change.

The pull applies the same rule in the other direction: before a pulled primary row is
written, any other local live primary is demoted (sync columns untouched — it mirrors
server state, it is not a local edit). Without this the local one-primary index makes the
`INSERT OR IGNORE` drop the pulled row **silently**, and the device re-pulls the server's
primary forever without ever storing it.

### 6.2 Pull (delta)

```
cursor = sync_state.last_pulled_at ?: EPOCH
remote = supabase.from(<table>)
    .select()
    .gte("updated_at", cursor - OVERLAP)      # OVERLAP = 5s, see below
    .order("updated_at")
    .limit(PAGE)
for each remote row:
    local = SELECT * FROM <table> WHERE id = remote.id
    when:
        local == null            -> insert as SYNCED (remote_version = remote.updated_at)
        local.sync_status == SYNCED -> overwrite as SYNCED
        local.sync_status == PENDING -> resolve by LWW (§7)
in one transaction with the rows above:
    sync_state.last_pulled_at = max(remote.updated_at)
repeat while a full page came back
```

- **`gte` with a 5-second overlap, not `gt`.** Two rows can share an `updated_at`, and
  clock/replication skew is real; re-reading a few rows is free (the apply is idempotent),
  while missing one is permanent data loss.
- **Soft-deleted rows are pulled too** — `deleted_at IS NOT NULL` is how a delete made on
  another device reaches this one. Never filter `deleted_at` in the *sync* query; filter it
  in the *read* queries.
- **Cursor commits with the rows**, in the same transaction. A pull killed halfway leaves a
  consistent cursor and simply re-fetches the rest next run.

---

## 7. Conflict resolution

MVP is **last-write-wins by `updated_at`**, evaluated per row (not per field). It only
matters when a local row is `PENDING` *and* the server has a newer version — which in a
single-device MVP means a reinstall or a second device, i.e. rare.

| Situation | Resolution |
| --- | --- |
| local `SYNCED`, remote newer | remote wins (ordinary update) |
| local `PENDING`, local `updated_at` > remote | local wins — leave `PENDING`; the next push overwrites the server |
| local `PENDING`, remote `updated_at` > local | remote wins — overwrite local, mark `SYNCED`, **log a resolved-conflict event with both timestamps** |
| local `PENDING`, timestamps equal | local wins (bias to the user's device; the push is a no-op if identical) |
| row deleted remotely, edited locally | delete wins — a deletion is a stronger intent than an edit |
| bill images | never conflict; immutable once uploaded |

The losing side is **never silently discarded**: every LWW resolution emits a structured
log + an analytics event, so a conflict storm is visible rather than mysterious.

True merge semantics are **Phase 2**, when multi-car/multi-device makes them real. The
`CONFLICT` status is already in use, but for the other thing it can mean: a row the *server*
refused for good (§6.1.1), not a row two devices disagree about.

---

## 8. Ordering, FKs, and blobs

**Topological push order** — a child row referencing a parent the server has never seen is
an FK violation:

```
profiles → cars → service_logs → bills → bill_line_items → documents → reminders
```

Pull uses the same order for the same reason. If a parent entity's `syncWith` returns
`false`, the engine **stops the run** — pushing children whose parent failed can only
produce FK errors. A permanent refusal is not one of those cases: those rows leave the
outbox and the entity still reports success (§6.1.1).

**Blobs are two-phase, upload first:**

1. Bill image is captured → stored locally, row references the local path,
   `storage_path = NULL`, `sync_status = PENDING`.
2. Push uploads to Storage at a deterministic path (`bills/{owner_id}/{bill_id}.jpg`) with
   upsert semantics, so a retry overwrites rather than duplicating.
3. Only after the upload succeeds is `storage_path` set and the row upserted.

Never the reverse order — a row pointing at a path that doesn't exist is a broken bill in
the Resale Passport, and passports are the trust product.

---

## 9. Sign-in adoption (the pre-auth data problem)

Odo lets someone set up a car, log services and scan bills **before** any account exists
(`LocalOwnerProvider` stamps a fixed placeholder `OwnerId`). Sync cannot run in that state:
there is no session, and `owner_id` is server-stamped from `auth.uid()` by trigger
(DB_SCHEMA §2) — clients cannot spoof it.

So the first successful sign-in runs a one-time **adoption** step, before the first sync:

```
in one transaction:
    UPDATE <every user-owned table>
       SET owner_id = <real auth uid>, sync_status = 'PENDING', updated_at = now()
     WHERE owner_id = <local placeholder>
```

Everything then flows up through the ordinary outbox — no special-case upload path. This
must be idempotent and safe to re-run (a crash mid-adoption leaves rows in either state,
both of which the next run handles).

**Sync never runs while `SessionStatusProvider.isSignedIn()` is false.** The scheduler
short-circuits; the app stays fully functional offline, exactly as designed.

---

## 10. Scheduling

The engine is pure `commonMain`. Only *scheduling* is platform-specific, via
`expect`/`actual` — which is where Odo diverges from NiA, since WorkManager is Android-only.

| Platform | `actual` | Notes |
| --- | --- | --- |
| Android | WorkManager unique work `OdoSync`, `ExistingWorkPolicy.KEEP` | `NetworkType.CONNECTED` constraint, exponential backoff off `Result.retry()`, expedited with non-expedited fallback |
| iOS | `BGTaskScheduler` | Phase 2 — MVP is Android-only (PRD) |

Triggers:

| Trigger | Policy |
| --- | --- |
| App start / foreground | enqueue unique work, `KEEP` (don't stack runs) |
| Connectivity regained | WorkManager constraint handles it — no manual listener |
| After a local write | debounced ~5s, coalesced — logging three entries fires one sync |
| FCM data push | server nudges a pull after a server-side change (reminders, entitlements) |
| Manual pull-to-refresh | `requestSync(SyncReason.Manual)`, bypasses the debounce |

Backoff is WorkManager's, driven by returning retry — **not** a hand-rolled loop inside the
engine. One retry policy, one place.

---

## 11. Observability

Sync is invisible when it works and infuriating when it doesn't, so it is instrumented from
day one via the existing `:observability:*` modules:

- **Logging** — one structured line per run: trigger reason, per-entity pushed/pulled
  counts, duration, outcome. Every LWW resolution logged with both timestamps.
- **Analytics** — `sync_completed` / `sync_failed` (with a coarse failure category),
  `sync_conflict_resolved`, `sync_rows_refused` (§6.1.1 — entity, count, HTTP status) and
  `sync_identity_adopted` (§6.1.2 — entity, count, which also counts reinstalls). Never log
  row contents; these are the user's records.
- **Performance** — a trace span per run, per-entity child spans, so a slow table is
  attributable.
- **Debug surface** — Profile → a developer row showing `pendingCount`, `lastSyncedAt` and
  `sync_state.last_error` per entity. Cheap to build, and the first thing anyone asks for
  when a user says "my data is missing".

---

## 12. Testing strategy

The engine must be fully testable with **no network and no Android**:

| Layer | How |
| --- | --- |
| `SyncEngine` ordering + failure propagation | fake `Syncable`s; assert order, and that a failing parent stops the run |
| Push | in-memory SQLDelight (`JdbcSqliteDriver`) + fake remote; assert idempotency (run twice → one row), and that a failed push leaves `PENDING` |
| Permanent refusal (§6.1.1) | fake remote refusing named ids; assert only the bad row goes `CONFLICT`, the rest sync, the run reports success, and a transient status still stops the entity |
| Identity adoption (§6.1.2) | fake remote holding the plate under another id; assert the local id and its children move, `created_at` comes across, a soft-deleted server row does not claim the plate, and a synced row makes no request |
| Pull | fake remote pages; assert cursor advance, resumability after a mid-run kill, soft-delete propagation |
| Conflicts | table-driven over §7's matrix — one test per row of that table |
| Adoption (§9) | placeholder → real uid, re-run safety |
| Scheduler | Android-only, `WorkManagerTestInitHelper` |

The §7 conflict matrix is the part most likely to rot; it gets an exhaustive test, not a
representative one.

---

## 13. Prerequisites & sequencing

The engine cannot be built until these exist. Each is its own slice:

| # | Prerequisite | Status |
| --- | --- | --- |
| 0 | `:core:sync` module — contracts + engine | **created** (contracts only, no engine) |
| 1 | `:core:network` module — supabase-kt client, DTOs, retry | **not created** (module isn't in `settings.gradle.kts`) |
| 2 | `:core:platform` module — connectivity, `SyncScheduler` actuals | **not created** |
| 3 | Real auth — `SessionStatusProvider` / `CurrentOwnerProvider` beyond the M1 stubs | stubbed (`LocalOwnerProvider`) |
| 4 | Sync columns on every local table + `sync_state` table | partial — `cars` has `sync_status` but **no `remote_version`** |
| 5 | Supabase project + tables + RLS deployed | not started |

Consequence for work happening now: **every new local table must ship §4.1's full column
set from its first migration.** Retrofitting a column onto a shipped table is a migration
we don't need to write.

---

## 14. Corrections owed to existing artifacts

Tracked here so they aren't lost; each is applied when the relevant code is next touched.

- `core/data/.../db/Car.sq` — the header comment says `sync_status` is
  `PENDING|SYNCED|FAILED`. It is `PENDING|SYNCED|CONFLICT` (§4.1). Also missing
  `remote_version`.
- `TDD.md` §8 — supersede the sketch with a pointer to this document.
- `ROADMAP.md` A.3/A.4 — updated: the engine lives in `:core:sync`, not `:core:data`.

---

## 15. Open questions (deliberately deferred)

1. **Page size / batch size** — pick against real data volumes; a heavy user has hundreds
   of service logs, not thousands, so this likely never matters.
2. **Bill image retention** — do we delete the local copy after a successful upload, or
   keep it as an offline cache? Leaning keep-with-LRU-eviction; the passport needs images
   offline.
3. **Multi-device conflict UX** — Phase 2, alongside `CONFLICT` status.
4. **Fairness pool contribution** — de-identified data points are pushed by an Edge
   Function, not this engine. Confirm when fairness aggregation is built.

---

*Odo Sync Engine Design — supersedes TDD §8. Revise here first; TDD points at this file.*
