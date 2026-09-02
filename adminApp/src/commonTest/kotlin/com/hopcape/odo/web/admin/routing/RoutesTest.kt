package com.hopcape.odo.web.admin.routing

import com.hopcape.odo.web.admin.domain.Permission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoutesTest {

    @Test
    fun `an empty path is the sign-in page`() {
        assertEquals(AdminRoute.SignIn, routeOf(""))
        assertEquals(AdminRoute.SignIn, routeOf("/"))
    }

    @Test
    fun `each section has its own segment`() {
        assertEquals(AdminRoute.Dashboard, routeOf("/dashboard"))
        assertEquals(AdminRoute.Users, routeOf("/users"))
        assertEquals(AdminRoute.Roles, routeOf("/roles"))
        assertEquals(AdminRoute.Content, routeOf("/content"))
        assertEquals(AdminRoute.Catalogue, routeOf("/catalogue"))
        assertEquals(AdminRoute.Vehicles, routeOf("/vehicles"))
        assertEquals(AdminRoute.Cities, routeOf("/cities"))
        assertEquals(AdminRoute.Tickets, routeOf("/tickets"))
        assertEquals(AdminRoute.Billing, routeOf("/billing"))
        assertEquals(AdminRoute.Flags, routeOf("/flags"))
        assertEquals(AdminRoute.Audit, routeOf("/audit"))
    }

    @Test
    fun `a trailing slash is the same page`() {
        assertEquals(AdminRoute.Vehicles, routeOf("/vehicles/"))
    }

    @Test
    fun `an unknown segment is not a page`() {
        assertEquals(AdminRoute.NotFound("/nope"), routeOf("/nope"))
    }

    /**
     * Nothing here is nested, and a URL that looks like it is should 404 rather
     * than resolving to its first segment — otherwise `/users/../delete` quietly
     * becomes the user list and somebody trusts a link that was never real.
     */
    @Test
    fun `a nested path is not a page`() {
        // Two levels are only a page for the sections that have a detail view.
        // Users is not one of them, and a nested path under it stays a 404.
        assertEquals(AdminRoute.NotFound("/users/42"), routeOf("/users/42"))
        assertEquals(AdminRoute.NotFound("/cities/42"), routeOf("/cities/42"))
        assertEquals(AdminRoute.NotFound("/tickets/1/reply"), routeOf("/tickets/1/reply"))
    }

    @Test
    fun `a ticket id that is not a number is a 404, not ticket zero`() {
        // The id comes out of a URL somebody may have typed or truncated, and
        // `toLongOrNull` is the difference between a 404 and silently opening the
        // wrong ticket.
        assertEquals(AdminRoute.NotFound("/tickets/abc"), routeOf("/tickets/abc"))
        assertEquals(AdminRoute.TicketDetail(7), routeOf("/tickets/7"))
    }

    @Test
    fun `detail routes round-trip through their location`() {
        // Same guarantee as the sections: a detail page reached by clicking a row
        // and one reached by refreshing its URL have to be the same page.
        listOf(
            AdminRoute.TicketDetail(1),
            AdminRoute.TicketDetail(9007199254740993L),
            AdminRoute.PostDetail("6b1f1a2c-0000-4000-8000-000000000001"),
            // Ids are uuids today, but the column is text and slugs are the obvious
            // next thing to put in it.
            AdminRoute.PostDetail("a slug with spaces"),
            AdminRoute.PostDetail("percent%and/slash"),
        ).forEach { route ->
            assertEquals(route, routeOf(route.location()), "round trip failed for $route")
        }
    }

    @Test
    fun `a detail page keeps its section lit in the rail`() {
        assertEquals(AdminRoute.Tickets, AdminRoute.TicketDetail(3).parent)
        assertEquals(AdminRoute.Content, AdminRoute.PostDetail("x").parent)
        // A section is its own parent, so the rail's comparison needs no special case.
        assertEquals(AdminRoute.Cities, AdminRoute.Cities.parent)
    }

    /**
     * The round trip is the point of the file. A route that formats to a path
     * that does not parse back is a link that 404s on refresh, and there is no
     * way to notice that by reading either function on its own.
     */
    @Test
    fun `every section round-trips through its location`() {
        (ADMIN_SECTIONS + AdminRoute.SignIn).forEach { route ->
            assertEquals(route, routeOf(route.location()), "round trip failed for $route")
        }
    }

    @Test
    fun `a 404 keeps the path it was reached at`() {
        assertEquals("/nope", AdminRoute.NotFound("/nope").location())
    }

    /**
     * Every section names a permission, so hiding a nav item and refusing a URL
     * come from the same fact. A section that forgot one would be reachable by
     * anybody who signed in — visible in the nav, and only stopped by RLS once
     * they tried to write something.
     */
    @Test
    fun `every section requires a permission`() {
        ADMIN_SECTIONS.forEach { route ->
            assertNotNull(route.permission, "$route has no permission and would be open to all staff")
        }
    }

    @Test
    fun `sign-in and not-found need no permission`() {
        assertNull(AdminRoute.SignIn.permission)
        assertNull(AdminRoute.NotFound("/x").permission)
    }

    /** The ids have to match the rows seeded in 20260831120000_admin_rbac.sql. */
    @Test
    fun `permission ids are the strings the database stores`() {
        assertEquals("catalog.vehicles.write", Permission.CatalogVehiclesWrite.id)
        assertEquals("admin.roles.write", Permission.AdminRolesWrite.id)
        assertEquals(Permission.AuditRead, Permission.ofId("audit.read"))
    }

    /**
     * The database is the source of truth and may grow a permission before this
     * build has a screen for it. Dropping the unknown one is right; failing to
     * sign in over it is not.
     */
    @Test
    fun `an unknown permission id is dropped rather than failing`() {
        assertNull(Permission.ofId("something.invented.later"))
    }
}
