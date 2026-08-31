package com.hopcape.odo.web.admin.routing

import com.hopcape.odo.web.admin.domain.Permission

/**
 * Every page the admin panel has, as a type.
 *
 * A route is not a URL. The URL is what the browser holds; this is what the app
 * switches on. [Routes] is the only place the two are converted into each other.
 *
 * One tree, not two. `:webApp` splits Public from Admin because it serves two
 * audiences out of one bundle; nothing here is ever reachable signed out except
 * [SignIn], so the split would be a distinction with one member on each side.
 *
 * [permission] is what makes the nav honest: every section names the permission
 * it needs, so hiding a nav item and refusing the URL come from the same fact
 * rather than from two lists that drift.
 */
sealed interface AdminRoute {

    /**
     * The permission this route needs, or null when it needs none.
     *
     * Null does not mean "public". It means the route is reachable by anyone who
     * got as far as the panel — the sign-in page, and the pages that explain why
     * you are seeing nothing.
     */
    val permission: Permission? get() = null

    /** `/admin` — sign in. The only route reachable signed out. */
    data object SignIn : AdminRoute

    /**
     * `/admin/vehicles` — the car catalog and the "my car isn't listed" queue.
     *
     * One route, not two. The queue and the catalog are the same table's inbox and
     * outbox, and a reviewer moves between them constantly; splitting the URL
     * would mean navigating away to check whether a make already exists.
     */
    data object Vehicles : AdminRoute {
        override val permission = Permission.CatalogVehiclesWrite
    }

    /** `/admin/cities` — the cities catalog and its queue. */
    data object Cities : AdminRoute {
        override val permission = Permission.CatalogCitiesWrite
    }

    /** `/admin/fairness` — the benchmark data the fairness engine reads. */
    data object Fairness : AdminRoute {
        override val permission = Permission.FairnessWrite
    }

    /** `/admin/users` — look somebody up, override an entitlement, restrict them. */
    data object Users : AdminRoute {
        override val permission = Permission.UsersRead
    }

    /** `/admin/audit` — who changed what, and when. */
    data object Audit : AdminRoute {
        override val permission = Permission.AuditRead
    }

    /** `/admin/staff` — who is an admin, and what roles they hold. */
    data object Staff : AdminRoute {
        override val permission = Permission.AdminRolesWrite
    }

    /**
     * `/admin/blog` — the CMS.
     *
     * A single route for now. #370 folds `:webApp`'s `Posts` / `Editor` / `Media`
     * / `Analytics` / `Settings` tree in under this prefix; until then it is the
     * placeholder that keeps the nav's shape honest.
     */
    data object Blog : AdminRoute {
        override val permission = Permission.BlogWrite
    }

    /**
     * Nothing matched.
     *
     * Carries the path that was tried so the URL can stay as it was typed.
     * Rewriting it would lose the evidence of what broke.
     */
    data class NotFound(val attempted: String) : AdminRoute
}

/** Every section the nav can draw, in the order it draws them. */
val ADMIN_SECTIONS: List<AdminRoute> = listOf(
    AdminRoute.Vehicles,
    AdminRoute.Cities,
    AdminRoute.Fairness,
    AdminRoute.Users,
    AdminRoute.Blog,
    AdminRoute.Audit,
    AdminRoute.Staff,
)
