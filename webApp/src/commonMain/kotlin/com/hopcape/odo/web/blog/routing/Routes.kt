package com.hopcape.odo.web.blog.routing

import com.hopcape.odo.web.blog.routing.BlogRoute.Admin
import com.hopcape.odo.web.blog.routing.BlogRoute.Public

/**
 * The only place a URL becomes a [BlogRoute] and back.
 *
 * Both directions live together so they cannot drift: a path shape that is
 * parsed but never produced is a link nobody can reach, and one produced but
 * never parsed is a link that 404s on refresh. The round trip is what the tests
 * check.
 */

/**
 * The first path segments that belong to the app rather than to a post.
 *
 * A post's URL is flat — `/blog/challan-kaise-check-karein` — so a post whose
 * slug were `search` would take the search page's URL. The content pipeline has
 * to reject these, and [routeOf] resolves them in the app's favour either way:
 * a broken link is better than a page that cannot be reached at all.
 */
val RESERVED_SLUGS: Set<String> = setOf("category", "author", "search", "admin")

/**
 * Reads a route out of a path and a query string.
 *
 * [path] is relative to wherever the app is mounted — the base is stripped by
 * the caller, because only the browser knows what it is. [query] is the raw
 * string after `?`, with or without the `?`.
 */
fun routeOf(path: String, query: String = ""): BlogRoute {
    val segments = path.split('/').filter { it.isNotBlank() }.map(::decode)

    return when {
        segments.isEmpty() -> Public.Index

        segments[0] == "admin" -> adminRouteOf(segments.drop(1), path)

        segments[0] == "category" ->
            segments.getOrNull(1)?.let(Public::Category) ?: Public.NotFound(path)

        segments[0] == "author" ->
            segments.getOrNull(1)?.let(Public::Author) ?: Public.NotFound(path)

        // Search takes no second segment: the term is a query parameter, so that
        // an empty search is still a page and not a different URL.
        segments[0] == "search" && segments.size == 1 -> Public.Search(queryTerm(query))

        segments.size == 1 && segments[0] !in RESERVED_SLUGS -> Public.Article(segments[0])

        else -> Public.NotFound(path)
    }
}

private fun adminRouteOf(segments: List<String>, path: String): BlogRoute = when {
    segments.isEmpty() -> Admin.SignIn
    segments[0] == "media" && segments.size == 1 -> Admin.Media
    segments[0] == "analytics" && segments.size == 1 -> Admin.Analytics
    segments[0] == "settings" && segments.size == 1 -> Admin.Settings
    segments[0] == "posts" && segments.size == 1 -> Admin.Posts
    segments[0] == "posts" && segments.size == 2 ->
        // "new" is the one id that means "there is no id yet".
        Admin.Editor(segments[1].takeUnless { it == "new" })
    else -> Public.NotFound(path)
}

/**
 * The location this route lives at, relative to the app's base.
 *
 * Always starts with `/` except for the index, which is the base itself. What
 * gets pushed into browser history is this with the base prepended.
 */
fun BlogRoute.location(): String = when (this) {
    Public.Index -> ""
    is Public.Article -> "/${encode(slug)}"
    is Public.Category -> "/category/${encode(slug)}"
    is Public.Author -> "/author/${encode(slug)}"
    is Public.Search -> if (query.isBlank()) "/search" else "/search?q=${encode(query)}"
    // Keeping the path a 404 was reached at is the whole point of holding it.
    is Public.NotFound -> attempted
    Admin.SignIn -> "/admin"
    Admin.Posts -> "/admin/posts"
    is Admin.Editor -> "/admin/posts/${postId?.let(::encode) ?: "new"}"
    Admin.Media -> "/admin/media"
    Admin.Analytics -> "/admin/analytics"
    Admin.Settings -> "/admin/settings"
}

/** Pulls `q` out of a raw query string. Any other parameter is ignored. */
private fun queryTerm(query: String): String =
    query.removePrefix("?")
        .split('&')
        .firstOrNull { it.startsWith("q=") }
        ?.removePrefix("q=")
        ?.let(::decode)
        .orEmpty()

/**
 * Percent-encodes everything a URL path or query value may not carry literally.
 *
 * Hand-rolled because `encodeURIComponent` is a browser function and this file
 * is common code — the parser is tested off-browser, so it cannot reach for one.
 * Unreserved characters are the RFC 3986 set; everything else goes out as its
 * UTF-8 bytes, which is what a search term in Devanagari needs.
 */
private fun encode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val character = byte.toInt().toChar()
        if (byte >= 0 && (character.isLetterOrDigit() || character in "-_.~")) {
            append(character)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

/** Reverses [encode]. `+` is a space, which is how a browser submits a form. */
private fun decode(value: String): String {
    if ('%' !in value && '+' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        when {
            character == '+' -> {
                bytes += ' '.code.toByte()
                index++
            }

            character == '%' && index + 2 < value.length -> {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(radix = 16)
                if (byte == null) {
                    // A stray '%' is a typo in a shared link, not a reason to fail.
                    bytes += character.code.toByte()
                    index++
                } else {
                    bytes += byte.toByte()
                    index += 3
                }
            }

            else -> {
                character.toString().encodeToByteArray().forEach { bytes += it }
                index++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

private const val HEX = "0123456789ABCDEF"
