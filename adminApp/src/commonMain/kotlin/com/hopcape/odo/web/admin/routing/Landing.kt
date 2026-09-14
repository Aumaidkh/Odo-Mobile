package com.hopcape.odo.web.admin.routing

import com.hopcape.odo.web.admin.domain.AdminSession

/**
 * Where somebody lands after signing in.
 *
 * The first section their role actually covers, in nav order — not a fixed home
 * page. A support admin holds `users.read` and nothing else, and sending them to
 * the vehicle catalog would greet every sign-in with "no access" for a section
 * they were never meant to see.
 *
 * Null means an account that is staff and holds no roles. That is a real state —
 * `seed_admin.sql` inserts the row and grants the role in two statements, and a
 * super admin can revoke the last one — so it has an answer of its own rather
 * than a blank page.
 */
fun landingFor(session: AdminSession): AdminRoute? =
    ADMIN_SECTIONS.firstOrNull { route ->
        val required = route.permission
        required != null && session.can(required)
    }

/**
 * The sections this session may see, in nav order.
 *
 * What the nav draws. Hiding the rest is a courtesy — the route guard refuses them
 * by URL too, and RLS refuses the data underneath either way.
 */
fun sectionsFor(session: AdminSession): List<AdminRoute> =
    ADMIN_SECTIONS.filter { route ->
        val required = route.permission
        required != null && session.can(required)
    }

/**
 * Whether this session may open this route at all.
 *
 * A route with no permission is open to anyone who got past sign-in — that is the
 * sign-in page and the pages that explain why you are seeing nothing, not a
 * loophole.
 */
fun AdminSession.mayOpen(route: AdminRoute): Boolean {
    val required = route.permission ?: return true
    return can(required)
}
