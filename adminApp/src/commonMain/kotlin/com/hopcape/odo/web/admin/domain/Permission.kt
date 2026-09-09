package com.hopcape.odo.web.admin.domain

/**
 * What an admin may do, mirroring `admin_role_permissions` in
 * `20260831120000_admin_rbac.sql`.
 *
 * **This is not the access control.** It decides what the nav draws and nothing
 * else. Every real permission question is answered by `admin_has()` inside an RLS
 * policy, at the moment of the write, where a browser cannot reach it — see D3 and
 * D6 in `docs/ADMIN_PANEL_PLAN.md`. A client that lied about this list would draw
 * itself a menu whose every button fails.
 *
 * [id] is the string the database stores. Kept explicit rather than derived from
 * the enum name so a rename here cannot silently stop matching a row there.
 */
enum class Permission(val id: String) {
    BlogWrite("blog.write"),
    CatalogVehiclesWrite("catalog.vehicles.write"),
    CatalogCitiesWrite("catalog.cities.write"),
    FairnessWrite("fairness.write"),
    UsersRead("users.read"),
    UsersEntitlementsWrite("users.entitlements.write"),
    UsersRestrictWrite("users.restrict.write"),
    AuditRead("audit.read"),
    AdminRolesWrite("admin.roles.write"),

    /**
     * Rollout percentages and kill switches.
     *
     * Separate from [AdminRolesWrite] on purpose: shipping a flag is an
     * engineering action somebody may hold without also being able to grant
     * themselves more access.
     */
    FlagsWrite("flags.write"),
    ;

    companion object {
        private val byId = entries.associateBy(Permission::id)

        /**
         * Null for a permission this build has never heard of.
         *
         * The database is the source of truth and may grow a permission before the
         * panel knows the word for it. Dropping the unknown one is right: it can
         * only ever have hidden a nav item that this build has no screen for.
         */
        fun ofId(id: String): Permission? = byId[id]
    }
}
