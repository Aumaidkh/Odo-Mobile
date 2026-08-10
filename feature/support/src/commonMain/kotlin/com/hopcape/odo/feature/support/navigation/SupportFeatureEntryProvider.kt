package com.hopcape.odo.feature.support.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.logging.api.LogUploadScheduler
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.feature.support.presentation.HelpSupportSheetContent
import com.hopcape.odo.feature.support.presentation.SupportPlaceholderScreen
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_chat
import com.hopcape.odo.feature.support.resources.sp_email
import com.hopcape.odo.feature.support.resources.sp_faqs
import com.hopcape.odo.feature.support.resources.sp_flag
import com.hopcape.odo.feature.support.resources.sp_idea
import com.hopcape.odo.feature.support.resources.sp_licences
import com.hopcape.odo.feature.support.resources.sp_privacy
import com.hopcape.odo.feature.support.resources.sp_rate
import com.hopcape.odo.feature.support.resources.sp_report
import com.hopcape.odo.feature.support.resources.sp_search
import com.hopcape.odo.feature.support.resources.sp_terms
import com.hopcape.odo.feature.support.resources.sp_tickets
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Support's contribution to the navigation graph — the whole [OdoDestination.Support]
 * group: the [OdoDestination.Support.Help] hub (a bottom-sheet destination) plus the
 * screen each of its rows opens. Collected by the `:app` host
 * (`getAll<FeatureEntryProvider>()`), so no other module references support directly.
 *
 * Every row destination currently renders [SupportPlaceholderScreen] — the graph and the
 * sheet are real, the screens behind them are not yet. Each stub is replaced in place as
 * its screen is built, which is why they are registered individually rather than folded
 * into one catch-all entry.
 *
 * [OdoDestination.Support.Email] and [OdoDestination.Support.Rate] end in a system
 * hand-off (a mail composer, the Play Store listing), so they hold a placeholder only
 * until `:core:platform` can perform the intent — then they leave the graph entirely.
 */
internal class SupportFeatureEntryProvider(
    private val navigationManager: NavigationManager,
    private val logUploadScheduler: LogUploadScheduler,
    private val appInfo: AppInfo,
) : FeatureEntryProvider {

    private val nm get() = navigationManager

    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Support.Help>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) {
            HelpSupportSheetContent(
                // Read from the installed package rather than from BuildInfo's compile-time
                // constants: this line ends up in a support ticket, so it should say what the
                // owner actually has installed.
                versionName = appInfo.versionName,
                versionCode = appInfo.versionCode,
                onClose = { nm.back() },
                onSearch = { nm.navigateTo(OdoDestination.Support.Search) },
                onChat = { nm.navigateTo(OdoDestination.Support.Chat) },
                onEmail = { nm.navigateTo(OdoDestination.Support.Email) },
                onTickets = { nm.navigateTo(OdoDestination.Support.Tickets) },
                onReportProblem = { nm.navigateTo(OdoDestination.Support.ReportProblem) },
                onSuggestIdea = { nm.navigateTo(OdoDestination.Support.SuggestIdea) },
                onFlagPriceData = { nm.navigateTo(OdoDestination.Support.FlagPriceData) },
                onRate = { nm.navigateTo(OdoDestination.Support.Rate) },
                onFaqs = { nm.navigateTo(OdoDestination.Support.Faqs) },
                onTerms = { nm.navigateTo(OdoDestination.Support.Terms) },
                onPrivacy = { nm.navigateTo(OdoDestination.Support.Privacy) },
                onLicences = { nm.navigateTo(OdoDestination.Support.Licences) },
                // "Send diagnostics" (docs/LOGGING_PLAN.md §9): queues an upload of whatever
                // is logged so far, regardless of auto-upload consent — an explicit tap here
                // is exactly what D3 (plan §1) means by "manual always available". Fire and
                // forget: WorkManager owns the retry, and the row has nothing further to show.
                onSendDiagnostics = { logUploadScheduler.requestUploadNow() },
            )
        }

        entry<OdoDestination.Support.Search> { Placeholder(Res.string.sp_search) }
        entry<OdoDestination.Support.Chat> { Placeholder(Res.string.sp_chat) }
        entry<OdoDestination.Support.Email> { Placeholder(Res.string.sp_email) }
        entry<OdoDestination.Support.Tickets> { Placeholder(Res.string.sp_tickets) }
        entry<OdoDestination.Support.ReportProblem> { Placeholder(Res.string.sp_report) }
        entry<OdoDestination.Support.SuggestIdea> { Placeholder(Res.string.sp_idea) }
        entry<OdoDestination.Support.FlagPriceData> { Placeholder(Res.string.sp_flag) }
        entry<OdoDestination.Support.Rate> { Placeholder(Res.string.sp_rate) }
        entry<OdoDestination.Support.Faqs> { Placeholder(Res.string.sp_faqs) }
        entry<OdoDestination.Support.Terms> { Placeholder(Res.string.sp_terms) }
        entry<OdoDestination.Support.Privacy> { Placeholder(Res.string.sp_privacy) }
        entry<OdoDestination.Support.Licences> { Placeholder(Res.string.sp_licences) }
    }

    /** The stub body, titled by the row that opened it, with back popping the entry. */
    @Composable
    private fun Placeholder(title: StringResource) {
        SupportPlaceholderScreen(title = stringResource(title), onBack = { nm.back() })
    }
}
