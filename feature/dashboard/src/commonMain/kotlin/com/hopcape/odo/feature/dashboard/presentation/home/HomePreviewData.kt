package com.hopcape.odo.feature.dashboard.presentation.home

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.cost.model.CostTrend
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.insight.model.CarInsight
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.WorkDone
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.WorkshopName
import com.hopcape.odo.feature.dashboard.domain.model.SetupProgress
import com.hopcape.odo.feature.dashboard.presentation.state.Loadable
import kotlinx.datetime.LocalDate

/**
 * Sample Home states for previews.
 *
 * Built from the same typed values the ViewModel produces, so a preview exercises the real
 * copy rules rather than a parallel set of display strings that can quietly stop matching
 * what the screen shows.
 */

private fun paise(value: Long): Amount = Amount.of(value).getOrElse { Amount.ZERO }

private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("preview km=$value") }

private val COMPLETE_SETUP = SetupProgress(
    carAdded = true,
    billScanned = true,
    documentsFiled = true,
    hasServiceLogs = true,
)

/** A scored car with a lapsed PUC and costs running above last quarter. */
internal fun previewNeedsAttention(): HomeUiState = HomeUiState(
    content = Loadable.Ready(
        HomeContent(
            userName = "Rahul",
            carName = "Swift VXI",
            odometer = km(54_000),
            score = 74,
            band = HealthBand.GOOD,
            scoreDelta = 6,
            perKm = paise(1_230),
            costTrend = CostTrend(percentChange = 4),
            overchargeTotal = paise(140_000),
            overchargesCaught = 2,
            attention = CarAttention.DocumentLapsed(
                documentId = DocumentId("doc-puc"),
                type = DocumentType.PUC,
                since = LocalDate(2026, 7, 25),
                daysAgo = 7,
            ),
            insight = CarInsight.CostMoved(percentChange = 14),
            recent = ActivityEvent.Service(
                id = ServiceLogId("l1"),
                workDone = WorkDone.Described(listOf("Oil change", "filter")),
                workshop = WorkshopName.of("Sharma Motors").getOrElse { null },
                odometer = km(53_400),
                amount = paise(320_000),
                verification = VerificationStatus.VERIFIED,
                overchargedBy = null,
                date = LocalDate(2026, 7, 12),
            ),
            setup = COMPLETE_SETUP,
            isNewUser = false,
        ),
    ),
)

/** Everything in order: nothing due, every service verified. */
internal fun previewAllClear(): HomeUiState = HomeUiState(
    content = Loadable.Ready(
        HomeContent(
            userName = "Rahul",
            carName = "Swift VXI",
            odometer = km(54_000),
            score = 88,
            band = HealthBand.EXCELLENT,
            scoreDelta = 0,
            perKm = paise(1_120),
            costTrend = CostTrend(percentChange = -5),
            overchargeTotal = paise(210_000),
            overchargesCaught = 3,
            attention = null,
            insight = CarInsight.ResaleReady(serviceCount = 6),
            recent = ActivityEvent.DocumentFiled(
                id = DocumentId("doc-ins"),
                document = DocumentType.INSURANCE,
                isRenewal = true,
                validTill = LocalDate(2027, 6, 30),
                date = LocalDate(2026, 7, 1),
            ),
            setup = COMPLETE_SETUP,
            isNewUser = false,
        ),
    ),
)

/** A car added today, with nothing logged or filed against it yet. */
internal fun previewNewUser(): HomeUiState = HomeUiState(
    content = Loadable.Ready(
        HomeContent(
            userName = "Rahul",
            carName = "Swift VXI",
            odometer = km(54_000),
            setup = SetupProgress(
                carAdded = true,
                billScanned = false,
                documentsFiled = false,
                hasServiceLogs = false,
            ),
            isNewUser = true,
        ),
    ),
)

/** Setup never finished — there is no car to say anything about. */
internal fun previewNoCar(): HomeUiState = HomeUiState(content = Loadable.Ready(HomeContent()))

/** The wait before the first read lands. */
internal fun previewLoading(): HomeUiState = HomeUiState()
