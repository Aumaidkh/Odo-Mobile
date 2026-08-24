package com.hopcape.odo.web.blog.routing

import com.hopcape.odo.web.blog.routing.BlogRoute.Admin
import com.hopcape.odo.web.blog.routing.BlogRoute.Public
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesTest {

    @Test
    fun `an empty path is the index`() {
        assertEquals(Public.Index, routeOf(""))
        assertEquals(Public.Index, routeOf("/"))
    }

    @Test
    fun `a single segment is a post`() {
        assertEquals(
            Public.Article("how-to-check-challans"),
            routeOf("/how-to-check-challans"),
        )
    }

    @Test
    fun `a trailing slash is the same page`() {
        assertEquals(Public.Article("expired-puc"), routeOf("/expired-puc/"))
    }

    @Test
    fun `categories and authors sit behind their own segment`() {
        assertEquals(Public.Category("challans"), routeOf("/category/challans"))
        assertEquals(Public.Author("rahul-deshmukh"), routeOf("/author/rahul-deshmukh"))
    }

    @Test
    fun `a category with no name is not a page`() {
        assertEquals(Public.NotFound("/category"), routeOf("/category"))
    }

    @Test
    fun `the search term comes from the query not the path`() {
        assertEquals(Public.Search("challan"), routeOf("/search", "?q=challan"))
        assertEquals(Public.Search("challan"), routeOf("/search", "q=challan"))
    }

    @Test
    fun `search with nothing typed is still the search page`() {
        assertEquals(Public.Search(""), routeOf("/search"))
    }

    @Test
    fun `other query parameters are ignored`() {
        assertEquals(Public.Search("tyre"), routeOf("/search", "?utm_source=x&q=tyre&page=2"))
    }

    @Test
    fun `a search term survives its encoding`() {
        val route = Public.Search("how to check challans")
        assertEquals("/search?q=how%20to%20check%20challans", route.location())
        assertEquals(route, roundTrip(route))
    }

    @Test
    fun `a search term in devanagari survives its encoding`() {
        // Multi-byte UTF-8, which is the case a naive percent-encoder mangles.
        assertEquals(Public.Search("चालान"), roundTrip(Public.Search("चालान")))
    }

    @Test
    fun `admin has a page per section`() {
        assertEquals(Admin.SignIn, routeOf("/admin"))
        assertEquals(Admin.Posts, routeOf("/admin/posts"))
        assertEquals(Admin.Media, routeOf("/admin/media"))
        assertEquals(Admin.Analytics, routeOf("/admin/analytics"))
        assertEquals(Admin.Settings, routeOf("/admin/settings"))
    }

    @Test
    fun `a post that was never saved has no id`() {
        assertEquals(Admin.Editor(null), routeOf("/admin/posts/new"))
        assertEquals(Admin.Editor("expired-puc"), routeOf("/admin/posts/expired-puc"))
    }

    @Test
    fun `an unknown path keeps what was tried`() {
        assertEquals(Public.NotFound("/one/two/three"), routeOf("/one/two/three"))
        assertEquals(Public.NotFound("/admin/nothing-here"), routeOf("/admin/nothing-here"))
    }

    @Test
    fun `a reserved word is never read as a post`() {
        // The content pipeline has to reject these as slugs; if one ever slipped
        // through, the app's own page has to win or it becomes unreachable.
        RESERVED_SLUGS.forEach { reserved ->
            val route = routeOf("/$reserved")
            assertEquals(false, route is Public.Article, "'$reserved' was read as a post")
        }
    }

    @Test
    fun `every route formats to a path that parses back to it`() {
        // NotFound is left out on purpose: it formats to the path that failed, so
        // asking it to round-trip is asking a broken URL to become a valid one.
        val routes = listOf(
            Public.Index,
            Public.Article("brake-pad-prices"),
            Public.Category("service-costs"),
            Public.Author("rahul-deshmukh"),
            Public.Search("challan"),
            Public.Search(""),
            Admin.SignIn,
            Admin.Posts,
            Admin.Editor(null),
            Admin.Editor("expired-puc"),
            Admin.Media,
            Admin.Analytics,
            Admin.Settings,
        )
        routes.forEach { route ->
            assertEquals(route, roundTrip(route), "${route.location()} did not survive the round trip")
        }
    }

    @Test
    fun `the base path is read from where the app was loaded`() {
        assertEquals("/blog", basePathOf("/blog"))
        assertEquals("/blog", basePathOf("/blog/how-to-check-challans"))
        // The Gradle dev server serves the app at the root.
        assertEquals("", basePathOf("/"))
        // A different site that merely starts with the same letters is not us.
        assertEquals("", basePathOf("/blogging-tips"))
    }

    /** Formats a route the way [BrowserRouter] would, then reads it back. */
    private fun roundTrip(route: BlogRoute): BlogRoute {
        val location = route.location()
        val split = location.indexOf('?')
        return if (split < 0) {
            routeOf(location)
        } else {
            routeOf(location.take(split), location.substring(split))
        }
    }
}
