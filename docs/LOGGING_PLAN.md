# File Logging & Log Upload — Implementation Plan

Turns `:observability:logging` from a console-only logger into one that writes durable,
rotated log files on disk and ships them to Supabase Storage on WorkManager's schedule.

Today `FileSink` is a stub that `println`s `"Append log to file yet to be implemented"`, and
`OdoApplication` configures it with `filePath("app_logs.log")` — a bare filename with no
directory. Nothing has ever been written to disk. This plan makes that path real.

**The binding constraint: the public API does not change.** Every call site that uses
`HLogger`, `Logger`, `ScopedLogger` or `loggerConfig { }` today compiles and behaves
identically after this lands. Everything here is additive or internal (§3).

---

## §1 Locked decisions

Decided with the owner on 2026-08-08. Do not reopen silently.

| # | Decision | Consequence |
|---|----------|-------------|
| D1 | **Files, not a SQLite table.** Logs never enter `OdoDatabase`. | No `LogEntry.sq`, no migration, no interaction with the sync engine. Durability comes from the sealed-file protocol (§6.2), which is what a table would have bought. Rationale in §2.1. |
| D2 | **Gzip on seal.** A sealed file is `<DATE_TIME>.log.gz`. | NDJSON compresses ~8–10×, which decides both the on-disk retention budget and the user's mobile data bill. Cost: `gunzip` after an `adb pull` to read one on-device. |
| D3 | **Auto-upload is opt-in in release; manual upload always available.** | Release builds upload nothing until consent is recorded (DPDP). Debug/internal builds always upload. "Send diagnostics" in `:feature:support` is an explicit user action and is never gated. |
| D4 | **Pre-auth logs are held until login, then uploaded.** | Storage RLS keys on the first path segment being `owner_id`, so a file written before sign-in has nowhere to go. It stays sealed on disk, tagged with the device id, and uploads under the new `owner_id` on first successful login — still subject to the 7-day age cap, so it expires on its own if nobody signs in. Keeps onboarding and login failures diagnosable. |
| D5 | **`:observability:logging` stays commonMain-pure — ports only, no IO.** The Supabase upload adapter lives in `:infrastructure:supabase` alongside the existing adapters; the `java.io` file store and the WorkManager plumbing live in `:core:platform/androidMain`. No new Gradle module. | Module map in §2. This is a deliberate departure from `:observability:crashreporting`, which holds its own `DiskCrashFileStore` in `androidMain` (owner-sanctioned there because a dying process cannot cross a module seam asynchronously — logging has no such constraint). |

Decided from convention, not asked:

- **NDJSON**, one JSON object per line. Append-friendly, and a truncated tail costs one line
  rather than the file. The existing `FileSink.toJsonLine` already emits exactly this shape.
- **Drop oldest on buffer overflow**, never block the caller. The newest events are the ones
  nearest the failure being diagnosed.
- **The active file is never uploaded and never deleted by retention.** Only sealed files are
  eligible for either.
- **No per-file upload attempt counter.** Age-based retention (§6.3) already dead-letters
  anything stuck; a sidecar counter file would be a second source of truth.

---

## §2 Where things sit

```
:observability:logging          commonMain only. Ports + the whole decision layer.
  api/    LogFileStore, LogUploadTarget, LogUploadScheduler, LogUploadRunner,
          FileLoggingConfig                              ← new, public (implemented elsewhere)
  internal/sinks/  AsyncSink (new), FileSink (stub → real, over the port)
  internal/file/   RotationPolicy, LogRetentionPruner, LogFileNaming,
                   InMemoryLogFileStore, LogUploadCoordinator          ← new, internal

:core:platform/androidMain      The two Android seams.
  logging/AndroidLogFileStore              java.io append + atomic seal + gzip
  logging/LogUploadWorker                  CoroutineWorker, KoinComponent-resolved
  logging/WorkManagerLogUploadScheduler    implements LogUploadScheduler

:infrastructure:supabase        One class added. No new module.
  adapters/SupabaseLogUploader : LogUploadTarget   PUT into the private `app-logs` bucket

:androidApp
  OdoApplication                  config value + ProcessLifecycle flush + scheduler kick
```

Why this split and not another:

- **`:observability:logging` cannot depend on Supabase.** `:infrastructure:supabase` already
  depends on `:observability:logging` (its `SupabaseTelemetry` injects `Logger`). Inverting
  it is a cycle. So the uploader is a port, implemented outward — the exact shape
  `CrashSink`/`FirebaseCrashlyticsSink` and `AnalyticsSink`/`FirebaseAnalyticsSink` already use.
- **File IO belongs to `:core:platform`.** CLAUDE.md assigns that module "expect/actual for
  camera, secure storage, notifications, connectivity, **file IO**". It also already carries
  `androidx.work.runtime` for `WorkManagerSyncScheduler`, so WorkManager stays in exactly one
  module. `:core:platform` gaining a dependency on `:observability:logging` is safe — logging
  is a leaf with no project dependencies.
- **The uploader is a class inside `:infrastructure:supabase`, not a new module.** Owner's call,
  and the cheaper one: that module already holds `SupabaseEnvironment` (with `storageUrl`), the
  configured `HttpClient`, `AccessTokenProvider` and `SupabaseTelemetry` — all `internal`, so a
  separate module would have needed at least `SupabaseEnvironment` made public, or a duplicate
  of the URL and auth wiring. `SupabaseLogUploader` sits next to `SupabaseRemoteFileStorage`,
  takes the same four constructor dependencies, and is bound in `supabaseModule` behind the
  same `environment.isConfigured` guard. Nothing existing is moved or made public.

### §2.1 Why not a session table in SQLite

The "write the session to the DB continuously, then flush it all to a file and upload"
shape was considered and rejected:

| | SQLite session table | Append-only file |
|---|---|---|
| Write cost | `INSERT` + journal + index per event, contending with real queries on the same `OdoDatabase` | one `write()` on a buffered stream |
| Migration risk | the local DB has **no `.sqm` files** (CLAUDE.md) — a new table means `no such table` on every installed build | none |
| Sync blast radius | log rows sit in the database the sync engine walks | none |
| Query shape | logs are never queried by predicate — written once, read once, deleted | exactly the file workload |
| Upload | must serialize DB → file anyway | the file *is* the payload |
| Reclaiming space | `DELETE` then `VACUUM`; SQLite never shrinks on its own | `delete(file)` |

The durability the table was reaching for is real, and it is kept — as the sealed-file
protocol in §6.2, where an atomic rename is the transaction boundary.

---

## §3 The public API is frozen

Unchanged, byte for byte — no member added, removed, renamed or re-signed:

`Logger` · `LogLevel` · `TraceContext` · `ScopedLogger` · `HLogger` (including `init`,
`setSession`, `setTagLevelOverride`, `tag`, `flush`, `asLogger`) · `loggingModule` ·
`loggerConfig { }` · `LoggerConfig.Builder`'s existing methods · `StableLoggerApi`.

Exactly two additive changes to existing public types:

1. **`LoggerConfig` gains one optional property** at the end of the constructor, defaulted:
   ```kotlin
   val fileLogging: FileLoggingConfig? = null
   ```
   plus one `Builder` method, `fileLogging(FileLoggingConfig)`. Every existing construction
   site — including `OdoApplication`'s `loggerConfig { }` block — compiles untouched.

2. **`filePath` keeps its signature but stops being able to do anything on its own.**
   Building a real file sink from a bare path was the plan when this section was first
   written, before §2's module split (ports in `:observability:logging`, the on-disk store
   in `:core:platform`) made it clear this module cannot turn a path into working disk
   storage — only a constructed `LogFileStore` can, which is what `fileLogging` carries. So
   the factory now looks at `fileLogging` alone; a `filePath`-only config with no
   `fileLogging` builds no file sink, same as before. That is not a behavior change: the old
   file sink only ever printed a placeholder, so a `filePath`-only build has always written
   zero real bytes. `fileLogging` wins when both are present. Nothing is deprecated or
   deleted — `filePath` stays solely so existing construction sites keep compiling.

New public types are all ports another module implements, mirroring `CrashSink`:
`FileLoggingConfig`, `LogFileStore`, `LogFileHandle`, `LogUploadTarget`, `LogUploadResult`,
`LogUploadScheduler`, `LogUploadRunner`.

`loggingModule` adds bindings (a `LogUploadRunner` `single`) but its declaration —
`val loggingModule: Module` — is unchanged.

---

## §4 The sink chain

`LoggerFactory` composes it. One concern per wrapper, order visible in one place:

```
LoggerImpl
├─ SafeSink ─ RedactingSink ─ LogcatSink                        [entirely unchanged]
└─ SafeSink ─ RedactingSink ─ AsyncSink ─ FileSink              [AsyncSink inserted]
                              (new)      (stub → real)
```

The only edit to the existing composition is inserting `AsyncSink` between `RedactingSink`
and `FileSink`. `SafeSink`, `RedactingSink`, `RegexPiiRedactor`, `LogcatSink`, `LoggerImpl`,
`LogEvent`, `LogSink` are not touched.

Ordering is the design, not an accident:

- **Redact before async** — PII never enters the in-memory buffer, so it cannot surface in an
  ANR or OOM heap dump. It costs a short regex pass on the caller thread; that is the right
  trade.
- **Async before file** — the caller thread returns after an enqueue. One writer coroutine on
  `Dispatchers.IO` owns the file, so there is no lock contention and no ordering ambiguity.
- **Safe outermost** — catches a failure anywhere in the chain, not only in the leaf. Logging
  stays strictly additive; it can never become a new crash source.
- **Logcat is not wrapped in `AsyncSink`** — during debugging you want logcat synchronous and
  in order with the code that emitted it.

Level filtering stays where it already is, inside each sink's `minLevel` check. Pulling it out
into a `LevelFilterSink` would be a cleaner chain and a larger diff; it is not worth it here.

---

## §5 The write path

`AsyncSink` is the batching decorator:

- Bounded `ArrayDeque<LogEvent>` — 512 events, guarded by a coroutine `Mutex`, drained by a
  single writer coroutine (`Dispatchers.IO`), same KMP-native shape as analytics'
  `BatchDispatcher`.
- On overflow: drop the **oldest**, increment `droppedCount`, and emit one synthetic
  `logger_overflow{dropped=N}` line on the next successful flush. Never block, never a silent
  gap.

Flush triggers:

| Trigger | Threshold | Why |
|---|---|---|
| Size | ≥ 64 events or ≥ 32 KB | keeps the buffer shallow under load |
| Time | every 5 s | bounds how much a sudden kill can lose |
| Level | any `WARN`/`ERROR` flushes immediately | that is the event you need if the process dies next |
| Lifecycle | `ProcessLifecycleOwner` `onStop` → `Logger.flush()` | the app just became killable |
| Explicit | `Logger.flush()` / `HLogger.flush()`, and before every upload | already in the public API, now it does something |

No explicit `fsync` on the lifecycle trigger, despite an earlier draft of this section saying
otherwise: `AndroidLogFileStore.appendToActive` already calls `OutputStream.flush()` after
every append (not batched — see below), which is enough to survive a plain process kill (the
bytes are with the OS, which outlives the process). Real `fsync` only buys protection against
a kernel crash or power loss in the narrow window after backgrounding, which is disproportionate
durability for a diagnostics log.

`FileSink` becomes the leaf that serializes and appends. Its existing `toJsonLine` /
`quoteIfString` helpers are kept as-is; `write` changes to route through `LogFileStore`. No
`writeBatch` — rotation is decided per event (`RotationPolicy` runs before every `write`), and
a batch API would either skip that check mid-batch or reintroduce it at the same granularity
for no gain: `AsyncSink` batches are typically well under the 2 MB rotation threshold, so the
one extra `flush()` per event this costs is not worth the added complexity.

---

## §6 Files on disk

### §6.1 Naming

```
2026-08-08T14-32-05Z.log.active   ← being appended to right now
2026-08-08T14-32-05Z.log.gz       ← sealed, eligible for upload
```

UTC, colons stripped (illegal on some filesystems, awkward in URLs). The timestamp is when
the file was **opened**, not when it was sealed — so the name answers "which run is this?".
Directory is `filesDir/logs`, app-private, and excluded from cloud backup via the
`data_extraction_rules` / `full_backup_content` rules.

### §6.2 The sealed-file protocol

Seal = flush the buffer, close the stream, gzip, `rename(.log.active → .log.gz)`. The rename
is atomic, so a file's name is the whole truth about its state: `.active` means a writer owns
it, `.gz` means nobody does and it is safe to read, upload and delete. No lock file, no
partial upload, no "is this one finished?" heuristic. A process killed mid-write leaves a
`.active` file that the next launch seals on startup before opening its own.

### §6.3 Rotation and retention

`RotationPolicy` strategies, composed — roll when **any** fires:

1. **Session** — a new file on every cold start. This is the "one file per session" shape.
2. **Time** — roll at UTC midnight.
3. **Size** — roll when the active file reaches 2 MB.

A fourth path lands the file, without being a `RotationPolicy` in the above sense: an
**explicit `flush()`** — `ProcessLifecycleOwner.onStop`, or the upload coordinator preparing
to read sealed files — also seals whatever is currently open (§5's `Sealable` note; `FileSink`
implements it so the seal always carries its own accurate `LogFileStats`, never a guess). In
practice this makes "one file per session" closer to "one file per foreground period" — each
background transition closes a file — which reads as a feature for a diagnostics log (one
coherent usage window per file) rather than the churn it would be if every size/time-triggered
buffer drain did the same; only the explicit trigger does.

`LogRetentionPruner` runs after each seal, in this order:

1. delete sealed files older than **7 days**;
2. while the directory exceeds **20 MB**, delete the oldest sealed file;
3. while more than **10** sealed files remain, delete the oldest.

The active file is exempt from all three.

### §6.4 The port

```kotlin
interface LogFileStore {
    fun appendToActive(lines: List<String>)
    /** Atomic rename (+ gzip) and writes the `.meta` sidecar. Null when nothing was written. */
    fun sealActive(stats: LogFileStats): LogFileHandle?
    fun sealOrphans(): List<LogFileHandle>   // startup recovery for .active files left behind
    fun listSealed(): List<LogFileHandle>
    fun read(name: String): ByteArray?
    fun delete(name: String)                 // removes the .gz and its .meta together
    fun totalBytes(): Long
}

data class LogFileHandle(
    val name: String,
    val sizeBytes: Long,
    val openedAtMs: Long,
    val sealedAtMs: Long,
    /** Null when the sidecar is missing — see the orphan note below. */
    val stats: LogFileStats?,
)

data class LogFileStats(
    val lineCount: Int,
    val warnCount: Int,
    val errorCount: Int,
    val hadFatal: Boolean,
)
```

An orphan sealed at startup has no live counters, so its `.meta` is absent and `stats` is
null. The uploader sends SQL `NULL` for those columns rather than inventing a zero — "we don't
know" and "there were no errors" must not look the same on a triage dashboard.

`InMemoryLogFileStore` (commonMain, `internal`) is the default and the test double, and is
what iOS gets — the MVP is Android-only, so there is no iOS file store and no
`expect`/`actual`. `AndroidLogFileStore` (`:core:platform/androidMain`) is the real one.

---

## §7 Upload

### §7.1 Ports

```kotlin
interface LogUploadTarget {
    val name: String
    /** [file] carries the name, byte size, open/seal times and level counts (§6.4). */
    suspend fun upload(file: LogFileHandle, bytes: ByteArray): LogUploadResult
}

enum class LogUploadResult { DELIVERED, RETRY, REJECTED }

interface LogUploadScheduler {
    fun schedulePeriodic()
    fun requestUploadNow()
    fun cancel()
}

/** What the worker resolves and runs. Bound by `loggingModule`. */
interface LogUploadRunner {
    suspend fun uploadPending(): LogUploadOutcome     // Delivered(n) | Partial | Skipped
    fun setAutoUploadConsent(granted: Boolean)        // D3
}
```

`LogUploadCoordinator` (internal, commonMain) implements `LogUploadRunner`: flush the sink,
seal the active file if it has anything, then for each sealed file — read, upload, and on
`DELIVERED` or `REJECTED` delete it; on `RETRY` leave it and report partial. `REJECTED`
(a permanent 4xx) deletes anyway, so one poisoned file cannot wedge the queue forever — the
same dead-letter reasoning as `BatchDispatcher.dispatchOnce`.

### §7.2 WorkManager

Two unique works, both in `:core:platform/androidMain`:

| Unique name | Cadence | Constraints | Existing-work policy |
|---|---|---|---|
| `odo-log-upload-periodic` | every 6 h | `UNMETERED` + battery-not-low | `KEEP` |
| `odo-log-upload-now` | one-shot | `CONNECTED` | `REPLACE` |

`odo-log-upload-now` is requested when a fatal was recorded last session, or the user taps
"Send diagnostics". Backoff `EXPONENTIAL, 30s`. `LogUploadWorker` maps outcomes the way
`OdoSyncWorker` does — `Delivered` → `success()`, `Partial` → `retry()`, `Skipped`
(no consent, nothing sealed, not signed in) → `success()`, because retrying with backoff
would just burn wakeups.

### §7.3 Server side

New private bucket `app-logs`, path `{owner_id}/{device_id}/{file}.log.gz`, matching the
existing "first path segment is the owner" Storage RLS convention (DB_SCHEMA §7). Insert-only
for the owner; no client read grant. A 30-day server-side lifecycle rule.

**Log lines never enter Postgres.** What does is one row per uploaded file, so the fleet stays
triageable in SQL without the operational database carrying thousands of log rows per session:

```sql
create table public.log_uploads (
    id           uuid primary key default gen_random_uuid(),
    owner_id     uuid not null references auth.users(id) on delete cascade,
    device_id    text not null,
    storage_path text not null unique,          -- the object this row describes; one row IS
                                                 -- one file, and one file is one foreground
                                                 -- period (§6.3) — no separate session_id needed
    opened_at    timestamptz not null,          -- from the file name
    sealed_at    timestamptz not null,
    uploaded_at  timestamptz not null default now(),
    size_bytes   bigint not null,               -- compressed
    app_version  text not null,
    os_version   text,                          -- not populated by the client yet (see below)
    -- Nullable on purpose: a file recovered from a killed process has no counters.
    -- NULL means "unknown", which must not read as "zero errors" (§6.4).
    line_count   integer,
    warn_count   integer,
    error_count  integer,
    had_fatal    boolean
);

create index on public.log_uploads (owner_id, uploaded_at desc);
create index on public.log_uploads (app_version, uploaded_at desc);
create index on public.log_uploads (uploaded_at desc) where had_fatal;
```

Which answers "who has a file with a fatal in it", "did `error_count` spike on 1.4.0", "pull
that one user's last session" — and then you download exactly one object instead of grepping a
bucket.

Conventions that apply, and two that deliberately do not:

- `owner_id` is stamped by a `BEFORE INSERT` trigger from `auth.uid()`, not sent by the client
  (DB_SCHEMA convention — clients can't spoof it). RLS is deny-all with `INSERT` and `SELECT`
  granted on the flat `owner_id = (SELECT auth.uid())`; no `UPDATE`, no `DELETE`.
- **No sync columns**, and no local mirror table. The SYNC_DESIGN rule applies to local tables
  mirroring a server table; this one has no local counterpart. It is write-once server-side
  telemetry, not user content.
- **No soft delete.** `deleted_at` exists so user content survives a mistake; a log index row
  is neither. It hard-deletes with the account, and a `pg_cron` job prunes rows whose object
  the 30-day lifecycle rule has already removed.

**`app_version` / `os_version` / `device_id` — what the client actually has to work with.**
Every other Odo facade config (`CrashConfig`, `AnalyticsConfig`, `PerformanceConfig`) takes
these as plain strings `OdoApplication` reads from `BuildConfig`/`Build.*` at facade-init time
— none of them are exposed as a Koin-injectable port anywhere in the app, and `SupabaseLogUploader`
is built inside `supabaseModule`, common code with no `Build.*` access. Rather than invent a new
cross-cutting device-identity port for one auxiliary table:
- `app_version` comes from `:core:platform`'s `AppInfo.versionName` — a real, existing, already
  Koin-bound port, worth the one new `:infrastructure:supabase → :core:platform` dependency
  (no cycle: `:core:platform` does not depend back on it) since app version is this table's
  main motivating query ("did `error_count` spike on 1.4.0").
- `os_version` is **not populated by the client** — nullable, left for a follow-up that plumbs
  it through properly rather than a placeholder that would read as real data.
- `device_id` is a UUID generated **once per `SupabaseLogUploader` instance** (effectively once
  per process), not persisted across restarts. `SecureStore` was considered and rejected — its
  own doc is explicit that it exists for exactly one thing (the Supabase session) and is
  Keystore-backed, i.e. deliberately slow; a device id is not a secret and general-purpose use
  is out of its stated scope. A per-restart id still groups every row from one cold start
  together, which is most of this column's triage value; true cross-restart stability is a
  follow-up, not a blocker for L7.

**Ordering:** insert the row **after** the object upload returns 2xx, never before — otherwise
the index points at objects that don't exist. If the object lands and the row insert fails, the
coordinator still deletes the local file and records a non-fatal: a missing index row is a much
smaller problem than re-uploading the same file forever.

**Where the counts come from:** `FileSink` keeps `line_count` / `warn_count` / `error_count` /
`had_fatal` for the file it is currently writing. On seal the store persists them as a small
`<name>.meta` JSON sidecar next to the `.gz`, so they survive a process death and
`listSealed()` can return them on `LogFileHandle`. The pruner and the coordinator delete the
sidecar with its file.

**DB_SCHEMA.md owes the bucket + policy (§7) and this table (§13 retention note)** — written
as part of L7.

---

## §8 PII, consent and the logger's own failures

- Redaction happens **before** anything is buffered or written (§4). `RegexPiiRedactor` is
  reused unchanged.
- No plate numbers, owner names, phone numbers, addresses or bill photos reach a log line,
  redactor or not — the redactor is a backstop, not a licence (CLAUDE.md).
- Auto-upload is consent-gated in release (D3). Manual "Send diagnostics" is an explicit act
  and bypasses the gate by definition.
- **The logger must not report its own failures through itself.** As built (L9), this is
  `LoggerConfig.onInternalError: (Throwable) -> Unit`, wired by `OdoApplication` to
  `CrashRecorder.recordNonFatal`. It has to guard **two** call sites, not one:
  `SafeSink` (the synchronous `write`/`flush` a caller invokes directly) and, separately,
  `AsyncSink` itself — the actual delegate call happens later, on `AsyncSink`'s own writer
  coroutine, a call stack `SafeSink` never sees. Without the second guard a throwing delegate
  (a full disk, a permission error) would silently kill that coroutine's `while (true)` and
  end file logging for the rest of the process. Never a `Logger.error` from inside a sink.
  Named `AnalyticsTracker` events (`log_file_rotated`, `log_upload_succeeded`,
  `log_upload_failed`, `log_events_dropped`) were this section's original sketch and are **not
  built**: `:observability:logging` cannot depend on `:observability:analytics` without
  breaking D5's module-purity rule. What exists instead: per-file upload outcomes already go
  through `SupabaseTelemetry` (§7's `rejected`/`failed`, itself `Logger` + `CrashRecorder`),
  and a buffer overflow still writes a synthetic `logger_overflow` line into the file itself
  (§5) — visible to whoever reads that session's log, which is the whole point of a
  diagnostics file. A real `log_events_dropped` analytics event is a legitimate follow-up, not
  a silent gap: it just needs a decision on which module gets to depend on which.

---

## §9 Slices

Each one builds and is verifiable on its own. `:androidApp:assembleDebug` plus the touched
module's `testAndroidHostTest` — not the whole-project build.

### L1 — Config + port + in-memory store
`FileLoggingConfig`, `LogFileStore`, `LogFileHandle`, `LogFileStats`, `LogFileNaming` (the
`<DATE_TIME>` format, the `.active`/`.gz` suffix rules, and the `.meta` sidecar shape),
`InMemoryLogFileStore`. Pure commonMain, fully unit-tested against a fake clock. **No
behaviour change** — nothing is wired yet.

### L2 — Rotation + retention, over the port
`RotationPolicy` (session / midnight / size) and `LogRetentionPruner` (age → total size →
count, deleting each `.gz` with its `.meta`). `FileSink` rewritten to append through
`LogFileStore`, consult the policies, and keep the live `LogFileStats` counters it hands to
`sealActive`; its `toJsonLine` helpers kept. Tested against `InMemoryLogFileStore`, including
the orphan-with-null-stats path.

### L3 — `AsyncSink`
Ring buffer, the five flush triggers, drop-oldest with `droppedCount`, the synthetic overflow
line, `flush()` semantics. Tested with `kotlinx-coroutines-test` and a virtual clock.

### L4 — Compose the chain
`LoggerConfig.fileLogging` added; `LoggerFactory` inserts `AsyncSink` and honours the config;
`OdoApplication` swaps `filePath("app_logs.log")` for a real `File(filesDir, "logs")` and adds
the `ProcessLifecycleOwner` flush next to the existing `HAnalytics.flush()` observer. First
slice where a file actually appears on device (via `InMemoryLogFileStore` still — L5 makes it
durable).

### L5 — `AndroidLogFileStore`
`:core:platform/androidMain`, `java.io` buffered append, atomic seal, gzip, `.meta` sidecar
write/read, orphan recovery on startup. Bound in `corePlatformAndroidModule`.
`androidHostTest` over a temp dir.

### L6 — Upload ports + coordinator
`LogUploadTarget`, `LogUploadResult`, `LogUploadScheduler`, `LogUploadRunner`,
`LogUploadCoordinator`, plus the `loggingModule` binding. Tested with a fake target covering
delivered / retry / rejected / no-consent.

### L7 — Supabase upload adapter + `log_uploads`
`SupabaseLogUploader` in `:infrastructure:supabase/adapters`, bound in `supabaseModule` under
the existing `environment.isConfigured` guard. It does two calls in order: Storage `PUT`, then
the `log_uploads` insert through the existing `PostgrestClient` (§7.3) — never the reverse, and
a failed insert after a successful `PUT` is a non-fatal, not a `RETRY`. Plus the `app-logs`
bucket + RLS policy, the `log_uploads` table + trigger + policies, and the DB_SCHEMA.md deltas.
No new module and no visibility changes. Ktor `MockEngine` tests for 2xx / 5xx / 4xx →
`DELIVERED` / `RETRY` / `REJECTED`, plus PUT-ok/insert-fails and the null-stats orphan row.

Note that an unconfigured checkout binds no `LogUploadTarget` at all — same as every other
adapter there. `LogUploadCoordinator` must therefore treat a missing target as `Skipped`, not
as a failure, so a local build with no `local.properties` doesn't retry uploads forever.

### L8 — WorkManager
`LogUploadWorker` + `WorkManagerLogUploadScheduler` in `:core:platform/androidMain`, both work
requests, and the `OdoApplication` kick. Upload works end to end after this.

### L9 — Consent + "Send diagnostics" + observability sweep
The `:feature:support` action, the release consent gate wired to a real decision, the meta
event names from §8, and the pre-commit observability sweep across everything above.

---

## §10 Change footprint

Existing files edited — five, all `internal` except one property:

| File | Edit |
|---|---|
| `internal/sinks/FileSink.kt` | stub body → real append through `LogFileStore`; helpers kept |
| `internal/LoggerFactory.kt` | insert `AsyncSink` into the file branch; read `fileLogging` |
| `api/LoggerConfig.kt` | one optional property + one `Builder` method (§3) |
| `api/LoggingModule.kt` | one added `single` binding |
| `androidApp/…/OdoApplication.kt` | config value; `ProcessLifecycle` flush; scheduler kick |

Untouched: `Logger`, `HLogger`, `LogLevel`, `TraceContext`, `ScopedLogger`, `LoggerImpl`,
`LogEvent`, `LogSink`, `SafeSink`, `RedactingSink`, `LogcatSink`, `PiiRedactor`,
`RegexPiiRedactor`, `SafeMap` — and every existing test in the module.

---

## §11 Out of scope

- **iOS.** MVP is Android-only. iOS gets `InMemoryLogFileStore` and no upload; the ports are
  the seam for adding it later.
- **A remote streaming sink.** `LoggerConfig.remoteEndpoint` stays reserved and unused;
  batch file upload is the delivery mechanism.
- **`LevelFilterSink` / `SamplingSink`.** Deliberately dropped to keep the diff to the
  existing chain minimal (§4). Revisit only if a log flood shows up in the field.
- **Server-side log search / ingestion pipeline.** Files land in a bucket; whatever reads them
  is a separate piece of work.
- **Deleting `remoteEndpoint` or `filePath`.** API is frozen (§3).

---

## §12 Definition of done

- A debug build writes `filesDir/logs/<DATE_TIME>.log.active`, seals it on cold start, and
  keeps at most 10 sealed `.gz` files inside 20 MB / 7 days.
- Killing the app loses at most 5 seconds of `INFO`; loses no `WARN`/`ERROR`.
- A `.active` file left by a killed process is sealed on the next launch, not appended to.
- A sealed file uploads to `app-logs/{owner}/{device}/…` on Wi-Fi and is deleted locally; a
  5xx leaves it and retries with backoff; a 4xx deletes it.
- Every uploaded object has exactly one `log_uploads` row, and
  `select * from log_uploads where had_fatal` finds the session that crashed.
- Pre-login files upload after first sign-in (D4); nothing uploads in release without consent (D3).
- No PII in any line of a captured log file, checked by hand against a real onboarding run.
- Public API diff is limited to the two additive items in §3.
- `./gradlew :androidApp:assembleDebug` plus `:observability:logging:testAndroidHostTest`,
  `:core:platform:testAndroidHostTest`, `:infrastructure:supabase:testAndroidHostTest` green.
