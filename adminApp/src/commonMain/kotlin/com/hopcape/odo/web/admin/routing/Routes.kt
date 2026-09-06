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
        segments.size == 1 -> SECTIONS_BY_SEGMENT[segments[0]] ?: AdminRoute.NotFound(path)
        // Two levels, and only for the sections that have a detail page. A ticket
        // id that is not a number is a 404 rather than a ticket zero — the id comes
        // from a URL somebody may have typed.
        segments.size == 2 -> when (segments[0]) {
            "tickets" -> segments[1].toLongOrNull()?.let(AdminRoute::TicketDetail) ?: AdminRoute.NotFound(path)
            "content" -> AdminRoute.PostDetail(decode(segments[1]))
            else -> AdminRoute.NotFound(path)
        }
        else -> AdminRoute.NotFound(path)
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
    is AdminRoute.TicketDetail -> "/tickets/$id"
    is AdminRoute.PostDetail -> "/content/${encode(id)}"
    else -> "/${segmentOf(this)}"
}

/**
 * Percent-encodes a path segment.
 *
 * A post id is a uuid today and needs none of this, but the id is put into a URL
 * and read back out of one, and a round trip that only works for the shape it
 * happens to have is a round trip that breaks the day slugs are used instead.
 */
private fun encode(value: String): String = buildString {
    for (c in value) {
        if (c.isLetterOrDigit() || c in "-_.~") append(c)
        else for (b in c.toString().encodeToByteArray()) append('%').append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
    }
}

private fun decode(value: String): String {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) { bytes.add(hex.toByte()); i += 3; continue }
        }
        for (b in c.toString().encodeToByteArray()) bytes.add(b)
        i++
    }
    return bytes.toByteArray().decodeToString()
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
    "dashboard" to AdminRoute.Dashboard,
    "users" to AdminRoute.Users,
    "roles" to AdminRoute.Roles,
    "content" to AdminRoute.Content,
    "catalogue" to AdminRoute.Catalogue,
    "vehicles" to AdminRoute.Vehicles,
    "cities" to AdminRoute.Cities,
    "tickets" to AdminRoute.Tickets,
    "billing" to AdminRoute.Billing,
    "flags" to AdminRoute.Flags,
    "social" to AdminRoute.Social,
    "audit" to AdminRoute.Audit,
)
