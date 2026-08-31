package com.hopcape.odo.web.admin.ui

import androidx.compose.runtime.Composable
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_nav_audit
import com.hopcape.odo.web.admin.resources.ad_nav_blog
import com.hopcape.odo.web.admin.resources.ad_nav_cities
import com.hopcape.odo.web.admin.resources.ad_nav_fairness
import com.hopcape.odo.web.admin.resources.ad_nav_staff
import com.hopcape.odo.web.admin.resources.ad_nav_users
import com.hopcape.odo.web.admin.resources.ad_nav_vehicles
import com.hopcape.odo.web.admin.resources.ad_not_found_title
import com.hopcape.odo.web.admin.resources.ad_signin_title
import com.hopcape.odo.web.admin.routing.AdminRoute
import org.jetbrains.compose.resources.stringResource

/**
 * What a section is called.
 *
 * One mapping, used by both the nav item and the page heading, because they are
 * the same name and two copies of it drift the moment one is renamed.
 *
 * Exhaustive on purpose — no `else` branch. A new section added to [AdminRoute]
 * stops compiling here until somebody writes its name, which is a better reminder
 * than a nav item that silently reads "Unknown".
 */
@Composable
fun AdminRoute.label(): String = when (this) {
    AdminRoute.Vehicles -> stringResource(Res.string.ad_nav_vehicles)
    AdminRoute.Cities -> stringResource(Res.string.ad_nav_cities)
    AdminRoute.Fairness -> stringResource(Res.string.ad_nav_fairness)
    AdminRoute.Users -> stringResource(Res.string.ad_nav_users)
    AdminRoute.Blog -> stringResource(Res.string.ad_nav_blog)
    AdminRoute.Audit -> stringResource(Res.string.ad_nav_audit)
    AdminRoute.Staff -> stringResource(Res.string.ad_nav_staff)
    AdminRoute.SignIn -> stringResource(Res.string.ad_signin_title)
    is AdminRoute.NotFound -> stringResource(Res.string.ad_not_found_title)
}
