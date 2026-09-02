package com.hopcape.odo.web.admin.routing

import com.hopcape.odo.web.admin.domain.AdminSession
import com.hopcape.odo.web.admin.domain.Permission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What each role may see.
 *
 * The permission sets below are the ones `20260831120000_admin_rbac.sql` seeds. If
 * that migration's grants change, these should fail — that is the point of writing
 * them out rather than deriving them.
 */
class LandingTest {

    private fun session(vararg permissions: Permission) = AdminSession(
        id = "admin-1",
        email = "someone@odoapp.in",
        name = "Someone",
        roles = emptyList(),
        permissions = permissions.toSet(),
    )

    private val superAdmin = session(*Permission.entries.toTypedArray())

    private val content = session(
        Permission.BlogWrite,
        Permission.CatalogVehiclesWrite,
        Permission.CatalogCitiesWrite,
        Permission.FairnessWrite,
    )

    private val support = session(
        Permission.UsersRead,
        Permission.UsersEntitlementsWrite,
        Permission.UsersRestrictWrite,
        Permission.AuditRead,
    )

    @Test
    fun `a super admin sees every section`() {
        assertEquals(ADMIN_SECTIONS, sectionsFor(superAdmin))
    }

    @Test
    fun `content sees the content and catalog sections, and not the users`() {
        assertEquals(
            listOf(AdminRoute.Content, AdminRoute.Catalogue, AdminRoute.Vehicles, AdminRoute.Cities),
            sectionsFor(content),
        )
    }

    @Test
    fun `support sees the user-facing sections, and not the catalogs`() {
        assertEquals(
            listOf(AdminRoute.Dashboard, AdminRoute.Users, AdminRoute.Tickets, AdminRoute.Billing, AdminRoute.Audit),
            sectionsFor(support),
        )
    }

    /**
     * The bug this replaced: sign-in landed everybody on the vehicle catalog, so a
     * support admin's first screen after every sign-in was "no access".
     */
    @Test
    fun `each role lands on a section it can actually open`() {
        listOf(superAdmin, content, support).forEach { session ->
            val landing = landingFor(session)
            assertTrue(landing != null && session.mayOpen(landing), "bad landing for $landing")
        }
        assertEquals(AdminRoute.Content, landingFor(content))
        assertEquals(AdminRoute.Dashboard, landingFor(support))
    }

    @Test
    fun `staff with no roles have nowhere to land`() {
        val noRoles = session()
        assertNull(landingFor(noRoles))
        assertEquals(emptyList(), sectionsFor(noRoles))
    }

    /**
     * Typing the URL of a hidden section is refused, not merely un-navigable.
     * The rail hiding it is a courtesy; this is the client-side gate, and RLS is
     * the one that actually matters.
     */
    @Test
    fun `a hidden section is refused when reached by URL`() {
        assertFalse(support.mayOpen(AdminRoute.Vehicles))
        assertFalse(content.mayOpen(AdminRoute.Users))
        assertFalse(content.mayOpen(AdminRoute.Roles))
        assertTrue(support.mayOpen(AdminRoute.Users))
    }

    /** Only super admin holds `admin.roles.write`, so only they edit the grid. */
    @Test
    fun `only a super admin sees the roles section`() {
        assertTrue(superAdmin.mayOpen(AdminRoute.Roles))
        assertFalse(content.mayOpen(AdminRoute.Roles))
        assertFalse(support.mayOpen(AdminRoute.Roles))
    }

    /** Sign-in and 404 carry no permission, so they are open to anyone signed in. */
    @Test
    fun `the permissionless routes are open to anybody who signed in`() {
        val noRoles = session()
        assertTrue(noRoles.mayOpen(AdminRoute.SignIn))
        assertTrue(noRoles.mayOpen(AdminRoute.NotFound("/x")))
    }

    /**
     * A section with no permission would be visible to every signed-in admin. The
     * nav derives from the same fact, so it would appear in the rail too — the
     * kind of hole that looks like a feature until somebody notices.
     */
    @Test
    fun `no section is reachable by an admin holding nothing`() {
        val noRoles = session()
        ADMIN_SECTIONS.forEach { route ->
            assertFalse(noRoles.mayOpen(route), "$route is open to an admin with no permissions")
        }
    }
}
