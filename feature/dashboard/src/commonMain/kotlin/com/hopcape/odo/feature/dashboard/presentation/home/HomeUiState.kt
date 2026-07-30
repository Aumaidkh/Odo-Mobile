package com.hopcape.odo.feature.dashboard.presentation.home

import androidx.compose.ui.graphics.vector.ImageVector
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.icons.IcLightbulbFilled
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.icons.IcTagFilled
import com.hopcape.odo.core.domain.shared.Amount

/** Tint/border role for a Home info card. */
internal enum class HomeCardTone { WARNING, SUCCESS, NEUTRAL }

/** A generic Home info card — the attention row (PUC / nothing-due) and the insight/resale row. */
internal data class HomeInfoCard(
    val icon: ImageVector,
    val tone: HomeCardTone,
    /** Small tracked-caps eyebrow above the title ("ODO INSIGHT" / "RESALE READY"); null hides it. */
    val eyebrow: String?,
    val title: String,
    val subtitle: String?,
    val hasChevron: Boolean,
)

/** The single "recent activity" row. */
internal data class HomeRecent(
    val logId: String,
    val icon: ImageVector,
    val title: String,
    val meta: String,
    /** Null for non-money events (e.g. a document renewal). */
    val amount: Amount?,
)

/** One row of the new-user setup checklist. */
internal data class HomeChecklistItem(
    val title: String,
    val subtitle: String,
    val done: Boolean,
    val hasChevron: Boolean,
)

/** Home display state — a scored dashboard, or the new-user setup path. */
internal sealed interface HomeState {
    val userName: String
    val carLine: String

    data class Scored(
        override val userName: String,
        override val carLine: String,
        val score: Int,
        val band: String,
        val healthNote: String,
        val runningCost: Amount,
        val costDelta: String,
        val costAboveAvg: Boolean,
        val saved: Amount,
        val savedSub: String,
        val attention: HomeInfoCard,
        val insight: HomeInfoCard,
        val recent: HomeRecent,
        val hasNotification: Boolean,
    ) : HomeState

    data class NewUser(
        override val userName: String,
        override val carLine: String,
        val checklist: List<HomeChecklistItem>,
    ) : HomeState
}

/** Sample: populated, needs attention (PUC expiring, cost above average). */
internal fun samplePopulated(): HomeState.Scored = HomeState.Scored(
    userName = "Rahul",
    carLine = "Swift VXI · 54,000 km",
    score = 74,
    band = "GOOD",
    healthNote = "Up 6 points this month after a verified service.",
    runningCost = paise(1_230),
    costDelta = "4% above avg",
    costAboveAvg = true,
    saved = rupees(1_400),
    savedSub = "2 overcharges caught",
    attention = HomeInfoCard(IcShieldFilled, HomeCardTone.WARNING, null, "PUC expires in 7 days", "Valid till 03 Aug 2026", hasChevron = true),
    insight = HomeInfoCard(IcLightbulbFilled, HomeCardTone.NEUTRAL, "ODO INSIGHT", "Oil change is due in about 1,000 km — plan it with your next fill.", null, hasChevron = false),
    recent = HomeRecent("l1", IcJournal, "Oil change + filter", "12 Jul · Verified", rupees(3_200)),
    hasNotification = true,
)

/** Sample: all clear (excellent score, nothing due). */
internal fun sampleAllClear(): HomeState.Scored = HomeState.Scored(
    userName = "Rahul",
    carLine = "Swift VXI · 54,000 km",
    score = 84,
    band = "EXCELLENT",
    healthNote = "Top 12% of similar Swifts in Pune.",
    runningCost = paise(1_120),
    costDelta = "5% below avg",
    costAboveAvg = false,
    saved = rupees(2_100),
    savedSub = "3 overcharges caught",
    attention = HomeInfoCard(IcCheck, HomeCardTone.SUCCESS, null, "Nothing needs attention", "All documents valid · no service due", hasChevron = false),
    insight = HomeInfoCard(IcTagFilled, HomeCardTone.NEUTRAL, "RESALE READY", "6 of 6 services verified — your record beats most listings.", null, hasChevron = false),
    recent = HomeRecent("l4", IcShieldFilled, "Insurance renewed", "01 Jul · SafeDrive", null),
    hasNotification = false,
)

/** Sample: brand-new user with no data yet. */
internal fun sampleNewUser(): HomeState.NewUser = HomeState.NewUser(
    userName = "Rahul",
    carLine = "Swift VXI · added today",
    checklist = listOf(
        HomeChecklistItem("Car added", "", done = true, hasChevron = false),
        HomeChecklistItem("Scan your last bill", "Unlocks fairness checks", done = false, hasChevron = true),
        HomeChecklistItem("Add insurance & PUC", "Never miss a renewal", done = false, hasChevron = true),
    ),
)

private fun paise(p: Long): Amount = Amount.of(p).getOrElse { Amount.ZERO }
private fun rupees(r: Long): Amount = paise(r * 100)
