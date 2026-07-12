package com.hopcape.odo.feature.fairnesscheck.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.navigation.FairnessLineInput
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessReportScreen

/**
 * Fairness-check's contribution to the navigation graph: the reusable
 * [OdoDestination.Fairness] entry. The route is structured as the utility contract —
 * **take the minimal input → run the domain [FairnessAnalyzer] → show the report** — so
 * any feature invokes fairness the same way (the caller only builds the input).
 */
internal class FairnessCheckFeatureEntryProvider(
    private val navigationManager: NavigationManager,
    private val analyzer: FairnessAnalyzer,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Fairness> { key -> FairnessCheckRoute(key, navigationManager, analyzer) }
    }
}

@Composable
internal fun FairnessCheckRoute(
    key: OdoDestination.Fairness,
    navigationManager: NavigationManager,
    analyzer: FairnessAnalyzer,
) {
    val query = remember(key) { key.toQuery() }
    val report: FairnessReport? by produceState<FairnessReport?>(initialValue = null, query) {
        value = analyzer.analyze(query)
    }
    when (val r = report) {
        null -> OdoScreen(title = "", onBack = { navigationManager.back() }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OdoTheme.colors.accent)
            }
        }
        else -> FairnessReportScreen(
            report = r,
            // Reporting an overcharge is car-scoped in the log; the demo uses the
            // placeholder ids the rest of the flow runs on (real ids thread through in M2).
            onReport = { navigationManager.navigateTo(OdoDestination.ServiceLog.ReportOvercharge(logId = DEMO_LOG_ID, carId = DEMO_CAR_ID)) },
            onDone = { navigationManager.back() },
            onBack = { navigationManager.back() },
        )
    }
}

/** Map the primitive nav input to the domain [FairnessQuery] (the domain never sees nav types). */
private fun OdoDestination.Fairness.toQuery(): FairnessQuery = FairnessQuery(
    city = city,
    items = items.map { it.toQueryItem() },
)

private fun FairnessLineInput.toQueryItem(): FairnessQueryItem = FairnessQueryItem(
    label = label,
    category = category?.let { name -> ServiceCategory.entries.firstOrNull { it.name == name } },
    amount = Amount.of(amountPaise).getOrElse { Amount.ZERO },
)

private const val DEMO_CAR_ID = "aaa"
private const val DEMO_LOG_ID = "aaa"
