package com.hopcape.odo.web.admin.ui.chrome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.hopcape.odo.web.admin.domain.AdminSession
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_shell_search
import com.hopcape.odo.web.admin.resources.ad_shell_wordmark
import com.hopcape.odo.web.admin.resources.ad_shell_wordmark_sub
import com.hopcape.odo.web.admin.resources.ad_sign_out
import com.hopcape.odo.web.admin.routing.AdminRoute
import com.hopcape.odo.web.admin.routing.sectionsFor
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Hairline
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.icon.BootstrapIcon
import com.hopcape.odo.web.admin.ui.icon.AdminIcons
import com.hopcape.odo.web.admin.ui.icon
import com.hopcape.odo.web.admin.ui.label
import com.hopcape.odo.web.admin.ui.labelResource
import com.hopcape.odo.web.admin.ui.subtitle
import androidx.compose.ui.graphics.RectangleShape
import com.hopcape.odo.web.admin.ui.theme.AdminElevation
import com.hopcape.odo.web.admin.ui.theme.raised
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.LocalAdminPalette
import com.hopcape.odo.web.admin.ui.theme.AdminType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The frame every signed-in page is drawn inside: a 236dp rail, then the page.
 *
 * **The rail only lists what this session may open.** That is a courtesy, not the
 * control — the same permission is checked again when a route is opened by URL,
 * and RLS refuses the data underneath either way.
 *
 * A rail rather than a top bar because there are eleven sections and horizontal
 * space runs out; a column scrolls.
 */
@Composable
fun AdminShell(
    session: AdminSession,
    current: AdminRoute,
    search: String,
    onSearch: (String) -> Unit,
    onNavigate: (AdminRoute) -> Unit,
    onSignOut: () -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize().background(AdminTokens.canvas)) {
        Rail(session, current, onNavigate, onSignOut, collapsed, onToggleCollapsed)
        Column(Modifier.fillMaxSize()) {
            Header(current, search, onSearch, session, dark, onToggleTheme)
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Collapsed to the marker column, or the full 236dp. */
private val RAIL_WIDE = 236.dp
private val RAIL_NARROW = 64.dp

@Composable
private fun Rail(
    session: AdminSession,
    current: AdminRoute,
    onNavigate: (AdminRoute) -> Unit,
    onSignOut: () -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
) {
    // Animated rather than snapped: the content column resizes with it, and a table
    // that jumps 172dp sideways loses whatever row somebody was reading.
    val width by animateDpAsState(
        targetValue = if (collapsed) RAIL_NARROW else RAIL_WIDE,
        animationSpec = tween(durationMillis = 220),
        label = "rail",
    )
    Row {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                // Square, not rounded: the rail runs the full height and its right
                // edge is the only one anybody sees. A rounded shape here would
                // round the top-left corner of the whole window.
                .raised(AdminElevation.chrome, RectangleShape)
                .background(AdminTokens.rail),
        ) {
            Wordmark(collapsed, onToggleCollapsed)
            Hairline(AdminTokens.railBorder)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                sectionsFor(session).forEach { route ->
                    // `key`, so each row keeps its own slot in the composition.
                    // Without it every row shares one position and anything
                    // remembered per row — a resource load, a hover source — is
                    // handed to whichever row composed there last.
                    key(route) {
                    NavItem(
                        // The resource, not the resolved string: NavItem does the
                        // one `stringResource` call. Resolving here would put a
                        // call site per row back where the bug was.
                        labelRes = route.labelResource(),
                        icon = route.icon(),
                        // `parent`, so opening a ticket keeps Tickets lit rather
                        // than clearing the rail and looking like it left the panel.
                        selected = route == current.parent,
                        // The dim badge is what marks a section the design calls
                        // for and nothing backs yet, so the rail is honest about
                        // which of these will actually do something.
                        badge = if (route.built) "" else "—",
                        collapsed = collapsed,
                        onClick = { onNavigate(route) },
                    )
                    }
                }
            }

            Hairline(AdminTokens.railBorder)
            Account(session, onSignOut, collapsed)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(AdminTokens.railBorder))
    }
}

/**
 * The Odo mark: two arcs of a gauge, the second overlaying the first.
 *
 * Drawn rather than shipped as an asset — it is two arcs, and a Canvas is smaller
 * than the plumbing that would load an SVG into a Wasm bundle.
 */
@Composable
private fun Wordmark(collapsed: Boolean, onToggleCollapsed: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Tighter when collapsed, so the mark centres in the narrow column
            // instead of hugging the left edge.
            .padding(
                start = if (collapsed) 20.dp else 18.dp,
                end = if (collapsed) 8.dp else 18.dp,
                top = 20.dp,
                bottom = 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Read before the Canvas: the draw lambda is a DrawScope, not a composable
        // scope, so a token cannot be resolved from inside it.
        val track = AdminTokens.border
        val dial = AdminTokens.text
        Canvas(Modifier.size(24.dp)) {
            val stroke = Stroke(width = size.minDimension * 0.245f, cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val arc = Size(size.width - stroke.width, size.height - stroke.width)
            // 135° start, matching the mockup's rotate(135). The track is three
            // quarters of the dial; the white arc is a little over half of it.
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arc,
                style = stroke,
            )
            drawArc(
                color = dial,
                startAngle = 135f,
                sweepAngle = 202f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arc,
                style = stroke,
            )
        }
        // The name is dropped when collapsed rather than ellipsised: "OD…" is not a
        // wordmark, and the mark beside it already says which product this is.
        if (!collapsed) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stringResource(Res.string.ad_shell_wordmark), style = AdminType.wordmark, color = AdminTokens.text)
                Text(
                    stringResource(Res.string.ad_shell_wordmark_sub),
                    style = AdminType.micro.copy(letterSpacing = 0.6.sp),
                    color = AdminTokens.textFaint,
                )
            }
            CollapseToggle(collapsed, onToggleCollapsed)
        }
    }
    // Expanded, the toggle sits beside the wordmark. Collapsed, there is no room
    // there, so it gets the row below — still the top of the rail, still the first
    // thing the pointer finds.
    if (collapsed) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CollapseToggle(collapsed, onToggleCollapsed)
        }
    }
}

/**
 * Dark or light, as a switch.
 *
 * A real switch rather than a sun/moon button, because a button that swaps its own
 * icon is ambiguous in exactly the way this control cannot afford: nobody can tell
 * whether the moon means "you are in dark" or "press for dark". A track with a knob
 * on one side reads as a position, which is what this is.
 *
 * Drawn rather than Material's `Switch`: that one brings its own colour roles and a
 * 52x32 touch target, and this sits in a 58dp bar beside a 26dp pill.
 */
@Composable
private fun ThemeSwitch(dark: Boolean, onToggle: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val knob by animateDpAsState(
        targetValue = if (dark) 2.dp else 18.dp,
        animationSpec = tween(durationMillis = 200),
        label = "knob",
    )
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(AdminTokens.field)
            .border(
                1.dp,
                if (hovered) AdminTokens.borderHover else AdminTokens.border,
                RoundedCornerShape(999.dp),
            )
            .clickable(interactionSource = interactions, indication = null, onClick = onToggle),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = knob)
                .size(18.dp)
                .clip(RoundedCornerShape(999.dp))
                // Amber on the light side: the knob is the only thing moving, and on
                // a white track a white knob is invisible.
                .background(if (dark) AdminTokens.textFaint else AdminTokens.accent),
        )
    }
}

/** Sign out, as the icon alone, for the collapsed rail. */
@Composable
private fun SignOutIcon(onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) AdminTokens.railHover else Color.Transparent)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BootstrapIcon(
            icon = AdminIcons.SignOut,
            tint = if (hovered) AdminTokens.danger else AdminTokens.textMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

/**
 * The chevron that folds the rail.
 *
 * Rotated 180° to point the other way rather than swapped for `chevron-right`: the
 * turn is animated, and a glyph that changes identity mid-animation flickers.
 */
@Composable
private fun CollapseToggle(collapsed: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val turn by animateFloatAsState(
        targetValue = if (collapsed) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "chevron",
    )
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered) AdminTokens.railHover else Color.Transparent)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BootstrapIcon(
            icon = AdminIcons.ChevronLeft,
            tint = if (hovered) AdminTokens.text else AdminTokens.textFaint,
            modifier = Modifier.size(11.dp).rotate(turn),
        )
    }
}

@Composable
private fun NavItem(
    labelRes: StringResource,
    icon: BootstrapIcon,
    selected: Boolean,
    badge: String,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> AdminTokens.railSelected
                    hovered -> AdminTokens.railHover
                    else -> Color.Transparent
                },
            )
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(10.dp),
    ) {
        // The icon replaced a 6dp square. Collapsed it is the entire nav item, so
        // it is drawn a shade larger there — 16dp of glyph in a 64dp column, where
        // the dot it replaced was a speck that said nothing about which section a
        // row was.
        //
        // Unselected sits at textMuted rather than textDim: an icon carries less
        // ink than a word, and at textDim these read as disabled rather than as
        // merely not-current.
        BootstrapIcon(
            icon = icon,
            tint = when {
                selected -> AdminTokens.text
                else -> AdminTokens.textMuted
            },
            modifier = Modifier.size(if (collapsed) 17.dp else 15.dp),
        )
        // Dropped, not ellipsised. A 64dp column fits no useful amount of a label,
        // and "Roles & p…" is noise where a marker is enough.
        if (!collapsed) {
            Text(
                // The one call site. Its argument changes per row; its position
                // does not.
                stringResource(labelRes),
                style = AdminType.navLabel,
                // textStrong, not textMuted: on a canvas the muted grey reads as
                // disabled rather than merely unselected.
                color = if (selected) AdminTokens.text else AdminTokens.textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (badge.isNotEmpty()) {
                Text(
                    badge,
                    style = AdminType.caption,
                    color = if (selected) AdminTokens.textStrong else AdminTokens.textDim,
                )
            }
        }
    }
}

/**
 * Who is signed in, at the foot of the rail.
 *
 * The address as well as the name: two people can share a display name, and the
 * audit log attributes changes to the account, so the panel shows the same thing
 * the log will.
 */
@Composable
private fun Account(session: AdminSession, onSignOut: () -> Unit, collapsed: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (collapsed) 8.dp else 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (collapsed) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).background(AdminTokens.text),
                contentAlignment = Alignment.Center,
            ) {
                Text(session.initials, style = AdminType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = AdminTokens.canvas)
            }
            if (!collapsed) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    session.name,
                    style = AdminType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = AdminTokens.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    session.roleLabel,
                    style = AdminType.micro,
                    color = AdminTokens.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Collapsed, sign out becomes the icon alone. It stays reachable on purpose:
        // hiding it would mean the only way out of the panel is to expand the rail
        // first, and the one action somebody may need in a hurry is the one that
        // ends the session.
        if (collapsed) {
            SignOutIcon(onSignOut)
        } else {
            RowAction(label = stringResource(Res.string.ad_sign_out), onClick = onSignOut)
        }
    }
}

/** The 58dp bar: what this page is, a search box, and who you are. */
@Composable
private fun Header(
    current: AdminRoute,
    search: String,
    onSearch: (String) -> Unit,
    session: AdminSession,
    dark: Boolean,
    onToggleTheme: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(AdminTokens.canvas)
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(current.label(), style = AdminType.title, color = AdminTokens.text, maxLines = 1)
            Text(
                current.subtitle(),
                style = AdminType.body,
                color = AdminTokens.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AdminField(
                value = search,
                onValueChange = onSearch,
                placeholder = stringResource(Res.string.ad_shell_search),
                modifier = Modifier.width(270.dp),
                leading = { Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(AdminTokens.textFaint)) },
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AdminTokens.field)
                    .border(1.dp, AdminTokens.border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(AdminTokens.text))
                Text(session.roleLabel, style = AdminType.strong, color = AdminTokens.textStrong, maxLines = 1)
            }
            ThemeSwitch(dark, onToggleTheme)
        }
        Hairline(AdminTokens.railBorder)
    }
}
