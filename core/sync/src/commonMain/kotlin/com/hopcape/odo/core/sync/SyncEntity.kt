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

    /**
     * The shared city lookup (`docs/SUPABASE_BOOTSTRAP.md` §2). No `owner_id` — every account
     * reads the same rows — so this is pull-only, the mirror image of
     * [CITY_SUBMISSIONS]'s push-only. Placed here rather than after [CARS]: it is foundational
     * reference data with no FK dependents, the same reason [PROFILES] leads the list.
     */
    CITIES,

    /**
     * A filed "my city isn't in the catalog" report. After [CITIES] would satisfy the FK — it
     * has none, since it names a city as free text — but it sits here because it is filed at
     * the same moment a profile is saved, the same way [VEHICLE_CATALOG_SUBMISSIONS] sits next
     * to [CARS].
     */
    CITY_SUBMISSIONS,

    CARS,

    /**
     * A filed "my car isn't in the catalog" report. Anywhere after [CARS] would satisfy the
     * FK — it has none, since it names a make/model as free text rather than referencing a
     * row — but it sits here because it is filed at the same moment a car is saved.
     */
    VEHICLE_CATALOG_SUBMISSIONS,

    /**
     * Automatically-detected drives (`docs/TRIPTRACKER_PLAN.md`). After [CARS] because a
     * trip only FKs to a car, not to a service log — nothing else references it.
     */
    TRIPS,
    /**
     * Tanks of fuel, confirmed by the owner. After [CARS] because a fill only FKs to a car,
     * and beside [TRIPS] for the same reason — neither references the other.
     *
     * Only *confirmed* fills are ever here. A detection waiting for an answer lives in
     * `pending_fills`, which is device-local and has no constant: it is a question this phone
     * has not asked yet, not a record of anything that happened.
     */
    FUEL_FILLS,
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
