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

    /**
     * A filed "report this overcharge". After [SERVICE_LOGS] because it references one.
     *
     * An entry's *categories* deliberately have no constant here: they are a projection of
     * their parent with no identity or lifecycle of their own, so they travel inside its
     * payload. A report is the opposite — its own record, filed once — which is what earns
     * it a cursor.
     */
    OVERCHARGE_REPORTS,
    BILLS,
    BILL_LINE_ITEMS,
    DOCUMENTS,

    /**
     * Score history. Anywhere after [CARS] would satisfy the FK; it sits last among the
     * built ones because it is the only entity whose loss costs nothing but a month delta —
     * every score is recomputed on read, so a run that dies before reaching it has still
     * saved everything the owner actually typed.
     */
    HEALTH_SCORES,
    REMINDERS,
}
