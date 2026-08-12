# `:core:sync`

> The **sync seam**. Defines what it means for a table to reconcile itself with the
> server (`Syncable`), what it may ask for while doing so (`Synchronizer`), the order
> that work runs in (`SyncEntity`), and how a run is asked for (`SyncScheduler`).
> It holds no database, no network client and no Android code — and depends on
> nothing.

- **Package:** `com.hopcape.odo.core.sync`
- **Gradle:** `:core:sync` · accessor `projects.core.sync`
- **Targets:** Android + iOS (KMP, `commonMain` only)
- **Dependencies:** none (stdlib). That is a design constraint, not an accident.
- **Design doc:** [`docs/SYNC_DESIGN.md`](../../docs/SYNC_DESIGN.md) — authoritative; this
  README is the map, that document is the reasoning.

> **Status: contracts only.** There is no implementation in this module yet. The engine
> lands in **M5**, once `:core:network`, `:core:platform` and real auth exist.

---

## 1. Why this module exists at all

The obvious placement for sync is `:core:data`, next to the repositories — which is where
Now in Android keeps its equivalents. The reason Odo splits it out is a dependency cycle:

- A `Syncable` must be implemented by the repository that owns the table, because only it
  knows how a row maps to a DTO. Those repositories live in `:core:data`.
- A repository also wants to request a sync after a local write.

So if the engine lived somewhere that depended on `:core:data`, `:core:data` would need it
right back. Gradle rejects that. Inverting the arrow solves it:

```
              :core:sync            contracts + engine. No DB, no network, no Android.
               ▲        ▲
               │        │
         :core:data   <platform worker module>
   (repos implement       (WorkManager worker + SyncScheduler actual, Android-only)
    Syncable)
```

The engine never goes looking for tables — it receives its `Syncable`s (`getAll<Syncable>()`
from Koin, wired in `:app`). The payoff: `:core:data` keeps its public surface and never
grows a Supabase or WorkManager dependency just to carry the engine, even though every
feature depends on it.

---

## 2. What's in here

| Type | What it is |
| --- | --- |
| `Syncable` | One table reconciling itself: `syncWith(synchronizer): Boolean`. Push then pull. `false` = retry the run. |
| `Synchronizer` | The three things a `Syncable` may do: read its cursor, advance it, record a failure. Deliberately nothing else. |
| `SyncCursor` | One entity's bookmark — the server `updated_at` high-water mark that makes a pull a *delta*. |
| `SyncEntity` | Every syncable table, **in the order it must sync**. Parents before children, or the server rejects the FK. |
| `SyncEngine` / `SyncResult` | Runs the `Syncable`s in order and reports what happened. Sequential, and a failing parent stops the run. |
| `SyncScheduler` / `SyncReason` | "Run a sync soon." The reason decides pacing — a burst of writes debounces into one run; a manual refresh doesn't wait. |

## 3. What is deliberately *not* in here

| Type | Where | Why |
| --- | --- | --- |
| `SyncStatus` (`PENDING`/`SYNCED`/`CONFLICT`) | `:core:data` | It's the vocabulary of a database column, not a concept the engine needs. |
| `SyncStatusProvider` | `:core:domain` | Features may depend on the domain, and must not depend on `:core:data` or on this module. |
| The WorkManager worker + `SyncScheduler` implementation | a platform module (M5) | Android-specific. Keeping it out is why this module stays `commonMain`-pure. |

---

## 4. If you're adding a new synced table

1. Give the local table the mandatory sync columns — `created_at`, `updated_at`,
   `deleted_at`, `remote_version`, `sync_status` (SYNC_DESIGN §4.1). From the **first**
   migration; retrofitting costs a migration nobody needs to write.
2. Add it to `SyncEntity`, **after** everything it references.
3. Have its repository implement `Syncable` and register it in the Koin graph.

Nothing in this module changes for step 3 — that's the point.
