package com.hopcape.odo.web.admin.routing

/**
 * The only place a URL becomes an [AdminRoute] and back.
 *
 * Both directions live together so they cannot drift: a path shape that is parsed
 * but never produced is a link nobody can reach, and one produced but never parsed
 * is a link that 404s on refresh. The round trip is what the tests check.
 */

/**
 * Reads a route out of a path.
 *
 * [path] is relative to wherever the app is mounted — the base is stripped by the
 * caller, because only the browser knows what it is.
 *
 * There is no query string to read. Nothing here is addressable by a search term
 * the way `/blog/search?q=` is; a filter typed into a catalog table is a control
 * on a page, not a page of its own.
 */
fun routeOf(path: String): AdminRoute {
    val segments = path.split('/').filter { it.isNotBlank() }

    // A single flat level. Nothing in this panel is nested, and a `when` over one
    // segment says so more plainly than a parser that could handle depth it will
    // never see.
    return when {
        segments.isEmpty() -> AdminRoute.SignIn
        segments.size > 1 -> AdminRoute.NotFound(path)
        else -> SECTIONS_BY_SEGMENT[segments[0]] ?: AdminRoute.NotFound(path)
    }
}

/**
 * The location this route lives at, relative to the app's base.
 *
 * Always starts with `/` except for sign-in, which is the base itself.
 */
fun AdminRoute.location(): String = when (this) {
    AdminRoute.SignIn -> ""
    // Keeping the path a 404 was reached at is the whole point of holding it.
    is AdminRoute.NotFound -> attempted
    else -> "/${segmentOf(this)}"
}

/**
 * The one segment a section owns.
 *
 * Derived from the route rather than written twice. [routeOf] and [location] both
 * read this map, so a new section is one entry and cannot be added to one
 * direction only.
 */
private fun segmentOf(route: AdminRoute): String =
    SECTIONS_BY_SEGMENT.entries.first { it.value == route }.key

private val SECTIONS_BY_SEGMENT: Map<String, AdminRoute> = mapOf(
    "vehicles" to AdminRoute.Vehicles,
    "cities" to AdminRoute.Cities,
    "fairness" to AdminRoute.Fairness,
    "users" to AdminRoute.Users,
    "blog" to AdminRoute.Blog,
    "audit" to AdminRoute.Audit,
    "staff" to AdminRoute.Staff,
)
