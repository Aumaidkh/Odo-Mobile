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
        assertEquals(AdminRoute.Vehicles, routeOf("/vehicles"))
        assertEquals(AdminRoute.Cities, routeOf("/cities"))
        assertEquals(AdminRoute.Fairness, routeOf("/fairness"))
        assertEquals(AdminRoute.Users, routeOf("/users"))
        assertEquals(AdminRoute.Blog, routeOf("/blog"))
        assertEquals(AdminRoute.Audit, routeOf("/audit"))
        assertEquals(AdminRoute.Staff, routeOf("/staff"))
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
        assertEquals(AdminRoute.NotFound("/users/42"), routeOf("/users/42"))
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
