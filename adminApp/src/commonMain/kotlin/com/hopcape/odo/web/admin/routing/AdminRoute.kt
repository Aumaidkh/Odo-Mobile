package com.hopcape.odo.web.admin.routing

import com.hopcape.odo.web.admin.domain.Permission

/**
 * Every page the admin panel has, as a type.
 *
 * A route is not a URL. The URL is what the browser holds; this is what the app
 * switches on. [Routes] is the only place the two are converted into each other.
 *
 * [permission] is what makes the nav honest: every section names the permission
 * it needs, so hiding a nav item and refusing the URL come from the same fact
 * rather than from two lists that drift.
 *
 * [built] separates the sections that talk to Supabase from the ones the design
 * calls for and nothing backs yet. A section that is not built draws a stated
 * empty state rather than sample numbers — a staff tool where a real figure and a
 * placeholder look alike is one where somebody eventually quotes the placeholder.
 */
sealed interface AdminRoute {

    /**
     * The permission this route needs, or null when it needs none.
     *
     * Null does not mean public. It means the route is reachable by anyone who got
     * as far as the panel — the sign-in page, and the pages that explain why you
     * are seeing nothing.
     */
    val permission: Permission? get() = null

    /** Whether there is a backend behind this section yet. */
    val built: Boolean get() = true

    /**
     * The section this route belongs under, for the nav's highlight.
     *
     * Itself for a section; the list for a detail page. Without it, opening a
     * ticket clears the rail's selection and the panel looks like it navigated
     * somewhere outside itself.
     */
    val parent: AdminRoute get() = this

    /** `/admin` — sign in. The only route reachable signed out. */
    data object SignIn : AdminRoute

    /** `/admin/dashboard` — what needs attention, and how the week is going. */
    data object Dashboard : AdminRoute {
        override val permission = Permission.UsersRead
    }

    /** `/admin/users` — find an account, override an entitlement, restrict it. */
    data object Users : AdminRoute {
        override val permission = Permission.UsersRead
    }

    /** `/admin/roles` — who holds which role, and what each role may do. */
    data object Roles : AdminRoute {
        override val permission = Permission.AdminRolesWrite
    }

    /**
     * `/admin/content` — the blog's posts.
     *
     * The list. One post opens at [PostDetail], where it is read and edited.
     */
    data object Content : AdminRoute {
        override val permission = Permission.BlogWrite
    }

    /** `/admin/catalogue` — service items, intervals and cost benchmarks. */
    data object Catalogue : AdminRoute {
        override val permission = Permission.FairnessWrite
    }

    /**
     * `/admin/vehicles` — the car catalog and the "my car isn't listed" queue.
     *
     * One route, not two. The queue and the catalog are the same table's inbox and
     * outbox, and a reviewer moves between them constantly.
     */
    data object Vehicles : AdminRoute {
        override val permission = Permission.CatalogVehiclesWrite
    }

    /** `/admin/cities` — the cities catalog and its queue. */
    data object Cities : AdminRoute {
        override val permission = Permission.CatalogCitiesWrite
    }

    /** `/admin/tickets` — the support queue. */
    data object Tickets : AdminRoute {
        override val permission = Permission.UsersRead
    }

    /** `/admin/billing` — subscriptions and revenue. */
    data object Billing : AdminRoute {
        override val permission = Permission.UsersEntitlementsWrite
    }

    /** `/admin/flags` — rollout by percentage, and the kill switches. */
    data object Flags : AdminRoute {
        override val permission = Permission.FlagsWrite
    }

    /**
     * `/admin/social` — the Instagram/Facebook pipeline: how it posts, when, to what, and
     * who may approve.
     *
     * On the content permission rather than one of its own. Publishing to the company's
     * social accounts is content work, and somebody who may write the blog is somebody who
     * may write a post.
     */
    data object Social : AdminRoute {
        override val permission = Permission.BlogWrite
    }

    /** `/admin/audit` — who changed what, and when. */
    data object Audit : AdminRoute {
        override val permission = Permission.AuditRead
    }

    /**
     * `/admin/tickets/<id>` — one ticket, in full.
     *
     * A route rather than an expanded row, because the body of a support ticket is
     * paragraphs and the reply history will be more: a queue that unfolds inline
     * pushes every other row off the screen, and the URL of the thing somebody is
     * working on is the one they need to paste to a colleague.
     */
    data class TicketDetail(val id: Long) : AdminRoute {
        override val permission = Permission.UsersRead
        override val parent: AdminRoute get() = Tickets
    }

    /**
     * `/admin/content/<id>` — one post, read and edited here.
     *
     * The blog's own editor is a separate app on a separate origin, and sending an
     * admin to its sign-in page to fix a typo is not editing from the panel.
     */
    data class PostDetail(val id: String) : AdminRoute {
        override val permission = Permission.BlogWrite
        override val parent: AdminRoute get() = Content
    }

    /**
     * Nothing matched.
     *
     * Carries the path that was tried so the URL can stay as it was typed.
     */
    data class NotFound(val attempted: String) : AdminRoute
}

/**
 * Every section the nav can draw, in the order it draws them.
 *
 * The mockup's nine, plus the two this panel has that it does not: Cities, which
 * is a separate catalog from the service one, and the audit log, which #369 needs
 * and which belongs at the bottom where a reference is looked up rather than
 * worked in.
 */
val ADMIN_SECTIONS: List<AdminRoute> = listOf(
    AdminRoute.Dashboard,
    AdminRoute.Users,
    AdminRoute.Roles,
    AdminRoute.Content,
    AdminRoute.Catalogue,
    AdminRoute.Vehicles,
    AdminRoute.Cities,
    AdminRoute.Tickets,
    AdminRoute.Billing,
    AdminRoute.Flags,
    AdminRoute.Social,
    AdminRoute.Audit,
)
