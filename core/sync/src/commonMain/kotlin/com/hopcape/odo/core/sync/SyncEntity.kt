package com.hopcape.odo.core.sync

/**
 * The syncable tables, **declared in the order they must be synced**.
 *
 * Order is the point of this enum, not just a listing: a child row referencing a parent
 * the server has never seen is an FK violation, so pushes (and pulls) walk this list top
 * to bottom. Adding an entity means placing it after everything it references.
 *
 * The `name` doubles as the `sync_state.entity` primary key, so renaming a constant
 * silently resets that entity's cursor — rename only with a migration.
 *
 * Design: [docs/SYNC_DESIGN.md] §8.
 */
enum class SyncEntity {
    PROFILES,
    CARS,
    SERVICE_LOGS,
    BILLS,
    BILL_LINE_ITEMS,
    DOCUMENTS,
    REMINDERS,
}
