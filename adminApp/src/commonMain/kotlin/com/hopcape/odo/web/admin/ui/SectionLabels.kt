package com.hopcape.odo.web.admin.ui

import androidx.compose.runtime.Composable
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_nav_audit
import com.hopcape.odo.web.admin.resources.ad_nav_billing
import com.hopcape.odo.web.admin.resources.ad_nav_catalogue
import com.hopcape.odo.web.admin.resources.ad_nav_cities
import com.hopcape.odo.web.admin.resources.ad_nav_content
import com.hopcape.odo.web.admin.resources.ad_nav_dashboard
import com.hopcape.odo.web.admin.resources.ad_nav_flags
import com.hopcape.odo.web.admin.resources.ad_nav_social
import com.hopcape.odo.web.admin.resources.ad_nav_roles
import com.hopcape.odo.web.admin.resources.ad_nav_tickets
import com.hopcape.odo.web.admin.resources.ad_nav_users
import com.hopcape.odo.web.admin.resources.ad_nav_vehicles
import com.hopcape.odo.web.admin.resources.ad_not_found_title
import com.hopcape.odo.web.admin.ui.icon.AdminIcons
import com.hopcape.odo.web.admin.ui.icon.BootstrapIcon
import com.hopcape.odo.web.admin.resources.ad_post_detail_title
import com.hopcape.odo.web.admin.resources.ad_sub_post_detail
import com.hopcape.odo.web.admin.resources.ad_sub_ticket_detail
import com.hopcape.odo.web.admin.resources.ad_ticket_detail_title
import com.hopcape.odo.web.admin.resources.ad_signin_title
import com.hopcape.odo.web.admin.resources.ad_sub_audit
import com.hopcape.odo.web.admin.resources.ad_sub_billing
import com.hopcape.odo.web.admin.resources.ad_sub_catalogue
import com.hopcape.odo.web.admin.resources.ad_sub_cities
import com.hopcape.odo.web.admin.resources.ad_sub_content
import com.hopcape.odo.web.admin.resources.ad_sub_dashboard
import com.hopcape.odo.web.admin.resources.ad_sub_flags
import com.hopcape.odo.web.admin.resources.ad_sub_social
import com.hopcape.odo.web.admin.resources.ad_sub_roles
import com.hopcape.odo.web.admin.resources.ad_sub_tickets
import com.hopcape.odo.web.admin.resources.ad_sub_users
import com.hopcape.odo.web.admin.resources.ad_sub_vehicles
import com.hopcape.odo.web.admin.routing.AdminRoute
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a section is called, and the line under it.
 *
 * One mapping for both the nav item and the page heading, because they are the
 * same name and two copies drift the moment one is renamed.
 *
 * Exhaustive on purpose — no `else`. A new section added to [AdminRoute] stops
 * compiling here until somebody writes its name, which is a better reminder than
 * a nav item that silently reads "Unknown".
 */
/**
 * The resource for a section's name, chosen **without composing**.
 *
 * This is not a style preference. The composable version below branches, and a
 * `when` whose every arm is its own `stringResource` call is eleven different call
 * sites at one position when it runs inside an unkeyed loop — which is exactly how
 * the rail draws it. Compose memoises by position, each row takes a different arm,
 * and `stringResource`'s async state never settles into the slot it belongs to. The
 * result is a rail of icons with no words next to them, for as long as the panel is
 * open. That shipped to production.
 *
 * Picking the resource here and resolving it at a single call site is what fixes it:
 * one `stringResource`, one slot, a changing argument.
 */
fun AdminRoute.labelResource(): StringResource = when (this) {
    AdminRoute.Dashboard -> Res.string.ad_nav_dashboard
    AdminRoute.Users -> Res.string.ad_nav_users
    AdminRoute.Roles -> Res.string.ad_nav_roles
    AdminRoute.Content -> Res.string.ad_nav_content
    AdminRoute.Catalogue -> Res.string.ad_nav_catalogue
    AdminRoute.Vehicles -> Res.string.ad_nav_vehicles
    AdminRoute.Cities -> Res.string.ad_nav_cities
    AdminRoute.Tickets -> Res.string.ad_nav_tickets
    AdminRoute.Billing -> Res.string.ad_nav_billing
    AdminRoute.Flags -> Res.string.ad_nav_flags
    AdminRoute.Social -> Res.string.ad_nav_social
    AdminRoute.Audit -> Res.string.ad_nav_audit
    AdminRoute.SignIn -> Res.string.ad_signin_title
    is AdminRoute.TicketDetail -> Res.string.ad_ticket_detail_title
    is AdminRoute.PostDetail -> Res.string.ad_post_detail_title
    is AdminRoute.NotFound -> Res.string.ad_not_found_title
}

@Composable
fun AdminRoute.label(): String = when (this) {
    AdminRoute.Dashboard -> stringResource(Res.string.ad_nav_dashboard)
    AdminRoute.Users -> stringResource(Res.string.ad_nav_users)
    AdminRoute.Roles -> stringResource(Res.string.ad_nav_roles)
    AdminRoute.Content -> stringResource(Res.string.ad_nav_content)
    AdminRoute.Catalogue -> stringResource(Res.string.ad_nav_catalogue)
    AdminRoute.Vehicles -> stringResource(Res.string.ad_nav_vehicles)
    AdminRoute.Cities -> stringResource(Res.string.ad_nav_cities)
    AdminRoute.Tickets -> stringResource(Res.string.ad_nav_tickets)
    AdminRoute.Billing -> stringResource(Res.string.ad_nav_billing)
    AdminRoute.Flags -> stringResource(Res.string.ad_nav_flags)
    AdminRoute.Social -> stringResource(Res.string.ad_nav_social)
    AdminRoute.Audit -> stringResource(Res.string.ad_nav_audit)
    AdminRoute.SignIn -> stringResource(Res.string.ad_signin_title)
    // A detail page is titled for the thing, not the section — the section's name
    // is still lit in the rail beside it, and repeating it says nothing.
    is AdminRoute.TicketDetail -> stringResource(Res.string.ad_ticket_detail_title, id)
    is AdminRoute.PostDetail -> stringResource(Res.string.ad_post_detail_title)
    is AdminRoute.NotFound -> stringResource(Res.string.ad_not_found_title)
}

/**
 * The icon a section is drawn with.
 *
 * Exhaustive like [label] and for the same reason: a new section stops compiling
 * here until somebody picks one, which beats a rail where one row silently has no
 * mark. The routes with no rail entry — sign-in, 404, the detail pages — take their
 * parent's, so a detail page opened by URL still lights the right row.
 */
fun AdminRoute.icon(): BootstrapIcon = when (this) {
    AdminRoute.Dashboard -> AdminIcons.Dashboard
    AdminRoute.Users -> AdminIcons.Users
    AdminRoute.Roles -> AdminIcons.Roles
    AdminRoute.Content -> AdminIcons.Content
    AdminRoute.Catalogue -> AdminIcons.Catalogue
    AdminRoute.Vehicles -> AdminIcons.Vehicles
    AdminRoute.Cities -> AdminIcons.Cities
    AdminRoute.Tickets -> AdminIcons.Tickets
    AdminRoute.Billing -> AdminIcons.Billing
    AdminRoute.Flags -> AdminIcons.Flags
    AdminRoute.Social -> AdminIcons.Content
    AdminRoute.Audit -> AdminIcons.Audit
    is AdminRoute.TicketDetail -> AdminIcons.Tickets
    is AdminRoute.PostDetail -> AdminIcons.Content
    AdminRoute.SignIn, is AdminRoute.NotFound -> AdminIcons.Dashboard
}

/** The line beside the title. Says what the page is for, not what it contains. */
@Composable
fun AdminRoute.subtitle(): String = when (this) {
    AdminRoute.Dashboard -> stringResource(Res.string.ad_sub_dashboard)
    AdminRoute.Users -> stringResource(Res.string.ad_sub_users)
    AdminRoute.Roles -> stringResource(Res.string.ad_sub_roles)
    AdminRoute.Content -> stringResource(Res.string.ad_sub_content)
    AdminRoute.Catalogue -> stringResource(Res.string.ad_sub_catalogue)
    AdminRoute.Vehicles -> stringResource(Res.string.ad_sub_vehicles)
    AdminRoute.Cities -> stringResource(Res.string.ad_sub_cities)
    AdminRoute.Tickets -> stringResource(Res.string.ad_sub_tickets)
    AdminRoute.Billing -> stringResource(Res.string.ad_sub_billing)
    AdminRoute.Flags -> stringResource(Res.string.ad_sub_flags)
    AdminRoute.Social -> stringResource(Res.string.ad_sub_social)
    AdminRoute.Audit -> stringResource(Res.string.ad_sub_audit)
    is AdminRoute.TicketDetail -> stringResource(Res.string.ad_sub_ticket_detail)
    is AdminRoute.PostDetail -> stringResource(Res.string.ad_sub_post_detail)
    AdminRoute.SignIn, is AdminRoute.NotFound -> ""
}
