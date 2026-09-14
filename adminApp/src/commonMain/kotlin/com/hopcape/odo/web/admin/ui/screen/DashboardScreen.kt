package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.AttentionItem
import com.hopcape.odo.web.admin.domain.DashboardSnapshot
import com.hopcape.odo.web.admin.domain.SignupDay
import com.hopcape.odo.web.admin.presentation.dashboard.DashboardEvent
import com.hopcape.odo.web.admin.presentation.dashboard.DashboardUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_dash_activity
import com.hopcape.odo.web.admin.resources.ad_audit_system
import com.hopcape.odo.web.admin.resources.ad_dash_activity_empty
import com.hopcape.odo.web.admin.resources.ad_dash_attention
import com.hopcape.odo.web.admin.resources.ad_dash_attention_clear
import com.hopcape.odo.web.admin.resources.ad_dash_attn_city
import com.hopcape.odo.web.admin.resources.ad_dash_attn_drafts
import com.hopcape.odo.web.admin.resources.ad_dash_attn_open
import com.hopcape.odo.web.admin.resources.ad_dash_attn_pastdue
import com.hopcape.odo.web.admin.resources.ad_dash_attn_urgent
import com.hopcape.odo.web.admin.resources.ad_dash_attn_vehicle
import com.hopcape.odo.web.admin.resources.ad_dash_delta_down
import com.hopcape.odo.web.admin.resources.ad_dash_delta_flat
import com.hopcape.odo.web.admin.resources.ad_dash_delta_up
import com.hopcape.odo.web.admin.resources.ad_dash_metric_cars
import com.hopcape.odo.web.admin.resources.ad_dash_metric_documents
import com.hopcape.odo.web.admin.resources.ad_dash_metric_logs
import com.hopcape.odo.web.admin.resources.ad_dash_metric_posts
import com.hopcape.odo.web.admin.resources.ad_dash_metric_pro
import com.hopcape.odo.web.admin.resources.ad_dash_metric_tickets
import com.hopcape.odo.web.admin.resources.ad_dash_metric_users
import com.hopcape.odo.web.admin.resources.ad_dash_signups
import com.hopcape.odo.web.admin.resources.ad_dash_signups_none
import com.hopcape.odo.web.admin.resources.ad_dash_sub_cars
import com.hopcape.odo.web.admin.resources.ad_dash_sub_pro
import com.hopcape.odo.web.admin.resources.ad_dash_sub_tickets
import com.hopcape.odo.web.admin.ui.component.LoadingPanel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.ReloadAction
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The landing screen.
 *
 * Ordered by what somebody opening the panel actually needs: what is waiting on a
 * person first, then the numbers, then the fortnight, then what changed. A
 * dashboard that leads with totals is a dashboard nobody acts on.
 */
@Composable
fun DashboardScreen(state: DashboardUiState, onEvent: (DashboardEvent) -> Unit) {
    val snapshot = state.value

    if (snapshot == null) {
        // Failure and loading both land here; the panel says which.
        val failure = state.snapshot as? Loadable.Failed
        LoadingPanel(
            message = failure?.message?.resolve(),
            onRetry = if (failure?.retryable == true) ({ onEvent(DashboardEvent.Refresh) }) else null,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { AttentionPanel(snapshot, state.busy, onEvent) }
        item { MetricRow(snapshot) }
        item { SignupsPanel(snapshot.signups) }
        item { ActivityPanel(snapshot) }
    }
}

@Composable
private fun AttentionPanel(snapshot: DashboardSnapshot, busy: Boolean, onEvent: (DashboardEvent) -> Unit) {
    val items = snapshot.attention
    Panel {
        // The reload sits here rather than on every panel: the whole screen is one
        // `admin_dashboard()` call, so one control re-reads all four of them.
        PanelHeader(stringResource(Res.string.ad_dash_attention)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Pill(
                    items.sumOf { it.count }.toString(),
                    dot = if (items.isEmpty()) null else AdminTokens.accent,
                )
                ReloadAction({ onEvent(DashboardEvent.Refresh) }, busy)
            }
        }
        if (items.isEmpty()) {
            Muted(stringResource(Res.string.ad_dash_attention_clear))
        } else {
            items.forEach { item ->
                key(item.kind) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(6.dp).height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (item.isUrgent) AdminTokens.accent else AdminTokens.textDim),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        // One call site, changing arguments.
                        stringResource(item.labelResource(), item.count),
                        style = AdminType.body,
                        color = AdminTokens.textStrong,
                        modifier = Modifier.weight(1f),
                    )
                    Text(item.count.toString(), style = AdminType.rowPrimary, color = AdminTokens.text)
                }
                }
            }
        }
    }
}

/** Urgent things get the accent dot; housekeeping does not. */
private val AttentionItem.isUrgent: Boolean
    get() = kind == AttentionItem.Kind.UrgentTickets || kind == AttentionItem.Kind.PastDueSubscriptions

/**
 * The resource for one attention row, picked **without composing**.
 *
 * Same reason as `AdminRoute.labelResource()`: a `when` whose every arm is its own
 * `stringResource` becomes six call sites at one position when it runs inside an
 * unkeyed loop, and this list is drawn with `forEach`. Compose memoises by
 * position, so the row's text never resolves and the count sits next to nothing.
 */
private fun AttentionItem.labelResource(): StringResource = when (kind) {
    AttentionItem.Kind.UrgentTickets -> Res.string.ad_dash_attn_urgent
    AttentionItem.Kind.OpenTickets -> Res.string.ad_dash_attn_open
    AttentionItem.Kind.VehicleSubmissions -> Res.string.ad_dash_attn_vehicle
    AttentionItem.Kind.CitySubmissions -> Res.string.ad_dash_attn_city
    AttentionItem.Kind.DraftPosts -> Res.string.ad_dash_attn_drafts
    AttentionItem.Kind.PastDueSubscriptions -> Res.string.ad_dash_attn_pastdue
}

@Composable
private fun MetricRow(snapshot: DashboardSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                stringResource(Res.string.ad_dash_metric_users),
                snapshot.users.toString(),
                delta = snapshot.signupDelta,
                sub = null,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                stringResource(Res.string.ad_dash_metric_cars),
                snapshot.cars.toString(),
                delta = null,
                sub = stringResource(Res.string.ad_dash_sub_cars, snapshot.serviceLogs),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                stringResource(Res.string.ad_dash_metric_pro),
                snapshot.subsActive.toString(),
                delta = null,
                sub = stringResource(Res.string.ad_dash_sub_pro, snapshot.subsPastDue),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                stringResource(Res.string.ad_dash_metric_tickets),
                snapshot.ticketsOpen.toString(),
                delta = null,
                sub = stringResource(Res.string.ad_dash_sub_tickets, snapshot.ticketsUrgent),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                stringResource(Res.string.ad_dash_metric_posts),
                snapshot.postsPublished.toString(),
                delta = null, sub = null, modifier = Modifier.weight(1f),
            )
            MetricCard(
                stringResource(Res.string.ad_dash_metric_logs),
                snapshot.serviceLogs.toString(),
                delta = null, sub = null, modifier = Modifier.weight(1f),
            )
            MetricCard(
                stringResource(Res.string.ad_dash_metric_documents),
                snapshot.documents.toString(),
                delta = null, sub = null, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(2f))
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    delta: Int?,
    sub: String?,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = AdminType.eyebrow, color = AdminTokens.textFaint)
            Spacer(Modifier.height(8.dp))
            Text(value, style = AdminType.metric, color = AdminTokens.text)
            Spacer(Modifier.height(4.dp))
            when {
                delta != null -> StatusText(
                    when {
                        delta > 0 -> stringResource(Res.string.ad_dash_delta_up, delta)
                        delta < 0 -> stringResource(Res.string.ad_dash_delta_down, -delta)
                        else -> stringResource(Res.string.ad_dash_delta_flat)
                    },
                    if (delta < 0) AdminTokens.danger else AdminTokens.textMuted,
                )
                sub != null -> Text(sub, style = AdminType.micro, color = AdminTokens.textDim)
                else -> Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Fourteen bars.
 *
 * Drawn with layout rather than a chart library: it is fourteen rectangles, and the
 * smallest charting dependency is larger than this whole screen. Heights are a
 * fraction of the tallest day, so a fortnight of ones still reads as a fortnight of
 * ones rather than as noise.
 */
@Composable
private fun SignupsPanel(days: List<SignupDay>) {
    val peak = days.maxOfOrNull { it.count } ?: 0
    Panel {
        PanelHeader(stringResource(Res.string.ad_dash_signups)) {
            Pill(days.sumOf { it.count }.toString())
        }
        if (peak == 0) {
            Muted(stringResource(Res.string.ad_dash_signups_none))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day -> Bar(day, peak, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Bar(day: SignupDay, peak: Int, modifier: Modifier = Modifier) {
    // Grows into place on first draw, so the chart arrives rather than appearing.
    val fraction by animateFloatAsState(
        targetValue = if (peak == 0) 0f else day.count.toFloat() / peak,
        animationSpec = tween(durationMillis = 520),
        label = "bar",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (day.count == 0) "" else day.count.toString(),
            style = AdminType.micro,
            color = AdminTokens.textDim,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.fillMaxWidth()
                    // A day with signups never draws as nothing: a hairline of colour
                    // is the difference between "none" and "one" at this scale.
                    .fillMaxHeight(if (day.count > 0) maxOf(fraction, 0.04f) else 0f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (day.count > 0) AdminTokens.accent else Color.Transparent),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            day.dayLabel,
            style = AdminType.micro,
            color = AdminTokens.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActivityPanel(snapshot: DashboardSnapshot) {
    Panel {
        PanelHeader(stringResource(Res.string.ad_dash_activity))
        if (snapshot.activity.isEmpty()) {
            Muted(stringResource(Res.string.ad_dash_activity_empty))
        } else {
            snapshot.activity.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${entry.action} ${entry.subjectType}",
                            style = AdminType.body,
                            color = AdminTokens.textStrong,
                        )
                        Text(
                            entry.actorEmail ?: stringResource(Res.string.ad_audit_system),
                            style = AdminType.micro,
                            color = AdminTokens.textDim,
                        )
                    }
                    Text(entry.at, style = AdminType.micro, color = AdminTokens.textDim)
                }
            }
        }
    }
}
