## What
Adds durable, rotating file logging to `:observability:logging` and a consent-gated pipeline
that uploads sealed log files to Supabase, plus a manual "Send diagnostics" action.

## Why
`HLogger`'s file sink was a stub — it printed a placeholder and never wrote a real byte, so a
crash-adjacent support ticket or a field bug report had nothing to look at but whatever the
owner could remember. Full design, locked decisions, and slice breakdown (L1–L9, all
implemented) are in `docs/LOGGING_PLAN.md`.

Refs: docs/LOGGING_PLAN.md

## How
- **Sink chain**: `FileSink` now writes NDJSON through a `LogFileStore` port instead of a bare
  path; a new `AsyncSink` decorator batches writes onto its own writer coroutine so callers
  never block on disk IO. Rotation is by size (2 MB) / UTC midnight / an explicit `flush()`
  (which also seals the current file — see `Sealable`, internal-only, not a public API change).
- **On-disk store**: `AndroidLogFileStore` (`:core:platform`) — gzip on seal, a `.meta` sidecar
  for per-file line/warn/error/fatal counts, orphan recovery for a `.active` file left by a
  killed process (its stats are `null`, never a guessed zero).
- **Upload**: `LogUploadCoordinator` (`:observability:logging`) → `SupabaseLogUploader`
  (`:infrastructure:supabase`) → new private `app-logs` bucket + `log_uploads` index table
  (one row per file, never per log line — DB_SCHEMA §9.20). PUT lands before the index insert,
  never the reverse; an insert failure after a good PUT is a non-fatal, not a retry.
  `WorkManagerLogUploadScheduler` runs a periodic pass (6h, unmetered + battery-not-low) plus a
  one-shot "now" request from `:feature:support`'s existing version-footer tap (previously an
  unimplemented `onCopyDiagnostics` TODO, now wired and renamed to `onSendDiagnostics`).
- **Consent (D3)**: auto-upload is opt-in in release, granted in debug, via
  `LogUploadRunner.setAutoUploadConsent`. "Send diagnostics" bypasses the gate — it's an
  explicit user action.
- **Public API**: `Logger`/`HLogger`/`LogLevel`/`TraceContext`/`ScopedLogger`/`loggingModule`
  are unchanged. `LoggerConfig` gains two optional, additive members —
  `fileLogging: FileLoggingConfig?` and `onInternalError: (Throwable) -> Unit` — everything
  else in the module is `internal`.
- **Two bugs the build itself caught**, fixed here: `SafeSink` only guards a caller's
  synchronous `write()`/`flush()`; the real disk write happens later on `AsyncSink`'s own
  writer coroutine, a call stack `SafeSink` never sees, so an uncaught exception there
  (full disk, permission error) would have silently killed that loop forever — fixed with a
  second guard inside `AsyncSink`, wired to `onInternalError` → `CrashRecorder.recordNonFatal`.
  Also guarded `LoggerFactory`'s startup-time `sealOrphans()` call, which runs during
  `HLogger.init()` and could otherwise crash cold start on a disk error.

## Testing
- [x] Unit tests added/updated — 107 in `:observability:logging`, 29 in `:core:platform`
      (incl. `AndroidLogFileStoreTest`), 62 in `:infrastructure:supabase` (incl.
      `SupabaseLogUploaderTest`, Ktor `MockEngine`-backed). All green.
- [x] `:androidApp:assembleDebug` green.
- [ ] Manually verified on device — not done this pass; worth a real-device check of file
      rotation, `.meta` sidecar contents, and an actual upload against a configured Supabase
      project before this ships.
- [x] Offline path checked — an unconfigured Supabase build binds no `LogUploadTarget`, and
      `LogUploadCoordinator` treats that as `Skipped`, not a failure (covered by test).

## Checklist
- [x] No secrets / keys / .env committed
- [x] Module boundaries respected — `:observability:logging` stays commonMain-pure, ports
      only; `AndroidLogFileStore`/WorkManager live in `:core:platform`; `SupabaseLogUploader`
      in `:infrastructure:supabase` (new dependency on `:core:platform` for `AppInfo`, no cycle)
- [x] Conventional-commit title
- [x] Docs updated — new `docs/LOGGING_PLAN.md`; `docs/DB_SCHEMA.md` §3/§7/§9.20/§10.2a/§12/§13
      updated for the `app-logs` bucket and `log_uploads` table

### Known gaps, called out rather than left silent
- `app-logs`/`crash` directories are not yet excluded from Android Auto Backup — the manifest
  has no `dataExtractionRules` at all today; log lines are already PII-redacted before they're
  written, so this is defense-in-depth, not an unredacted-PII gap.
- `log_uploads.os_version` is nullable and not populated by the client yet.
- `device_id` is a random UUID per `SupabaseLogUploader` instance, not persisted across app
  restarts (see `docs/LOGGING_PLAN.md` §7.3 for why `SecureStore` was rejected for this).
- The `log_file_rotated`/`log_upload_succeeded`/`log_upload_failed`/`log_events_dropped`
  named `AnalyticsTracker` events sketched in an early draft of the plan were not built:
  `:observability:logging` cannot depend on `:observability:analytics` without breaking its
  module-purity rule. The underlying signals are covered another way (see plan §8).

## Squash commit message
```
feat(logging): add file logging and log upload

Odo had no durable logging: HLogger's file sink was a stub that
printed a placeholder and never wrote a byte, so a crash-adjacent
support ticket had nothing to look at but memory. Adds a batched,
rotating, redacted NDJSON file sink behind AsyncSink, an on-disk
LogFileStore (:core:platform), and a WorkManager-scheduled upload
to a new Supabase app-logs bucket + log_uploads index table, gated
on consent (D3) except for an explicit "Send diagnostics" action.

Public Logger/HLogger API is unchanged except two additive members
(LoggerConfig.fileLogging, .onInternalError) — see docs/LOGGING_PLAN.md
for the full design, locked decisions, and slice breakdown (L1-L9).

Refs: docs/LOGGING_PLAN.md
```
