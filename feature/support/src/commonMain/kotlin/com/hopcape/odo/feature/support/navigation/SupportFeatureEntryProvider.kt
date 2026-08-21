package com.hopcape.odo.feature.support.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.logging.api.LogUploadScheduler
import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.app.DeviceInfo
import com.hopcape.odo.core.platform.mail.MailDraft
import com.hopcape.odo.core.platform.mail.rememberMailComposer
import com.hopcape.odo.core.platform.share.rememberTextSharer
import com.hopcape.odo.feature.support.presentation.HelpSupportSheetContent
import com.hopcape.odo.feature.support.presentation.PrivacyPolicyScreen
import com.hopcape.odo.feature.support.presentation.SupportPlaceholderScreen
import com.hopcape.odo.feature.support.presentation.faq.FaqsScreen
import com.hopcape.odo.feature.support.presentation.faq.SupportSearchScreen
import com.hopcape.odo.feature.support.presentation.feedback.FeedbackScreen
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_email
import com.hopcape.odo.feature.support.resources.sp_email_address
import com.hopcape.odo.feature.support.resources.sp_fb_flag_intro
import com.hopcape.odo.feature.support.resources.sp_fb_flag_subject
import com.hopcape.odo.feature.support.resources.sp_fb_idea_intro
import com.hopcape.odo.feature.support.resources.sp_fb_idea_subject
import com.hopcape.odo.feature.support.resources.sp_fb_mail_footer
import com.hopcape.odo.feature.support.resources.sp_fb_report_intro
import com.hopcape.odo.feature.support.resources.sp_fb_report_subject
import com.hopcape.odo.feature.support.resources.sp_flag
import com.hopcape.odo.feature.support.resources.sp_idea
import com.hopcape.odo.feature.support.resources.sp_licences
import com.hopcape.odo.feature.support.resources.sp_privacy
import com.hopcape.odo.feature.support.resources.sp_rate
import com.hopcape.odo.feature.support.resources.sp_report
import com.hopcape.odo.feature.support.resources.sp_terms
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
    private val deviceInfo: DeviceInfo,
    private val legalLinks: LegalLinks,
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
                onEmail = { nm.navigateTo(OdoDestination.Support.Email) },
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

        entry<OdoDestination.Support.Search> { SupportSearchScreen(onBack = { nm.back() }) }
        entry<OdoDestination.Support.Email> { Placeholder(Res.string.sp_email) }
        entry<OdoDestination.Support.ReportProblem> {
            FeedbackRoute(
                title = Res.string.sp_report,
                intro = Res.string.sp_fb_report_intro,
                subject = Res.string.sp_fb_report_subject,
            )
        }
        entry<OdoDestination.Support.SuggestIdea> {
            FeedbackRoute(
                title = Res.string.sp_idea,
                intro = Res.string.sp_fb_idea_intro,
                subject = Res.string.sp_fb_idea_subject,
            )
        }
        entry<OdoDestination.Support.FlagPriceData> {
            FeedbackRoute(
                title = Res.string.sp_flag,
                intro = Res.string.sp_fb_flag_intro,
                subject = Res.string.sp_fb_flag_subject,
            )
        }
        entry<OdoDestination.Support.Rate> { Placeholder(Res.string.sp_rate) }
        entry<OdoDestination.Support.Faqs> { FaqsScreen(onBack = { nm.back() }) }
        entry<OdoDestination.Support.Terms> { Placeholder(Res.string.sp_terms) }
        entry<OdoDestination.Support.Privacy> { PrivacyPolicyRoute() }
        entry<OdoDestination.Support.Licences> { Placeholder(Res.string.sp_licences) }
    }

    /**
     * One of the three feedback forms, wired to the mail app.
     *
     * The build and device footer is resolved once, not per keystroke: it does not depend on
     * what is being typed, and rebuilding it on every character would be work for nothing.
     *
     * Sending pops the form first, then opens the composer. The other order leaves somebody
     * returning from their mail app to a form still holding the message they just sent, with
     * no way to tell whether it went.
     */
    @Composable
    private fun FeedbackRoute(
        title: StringResource,
        intro: StringResource,
        subject: StringResource,
    ) {
        val composeMail = rememberMailComposer()
        val supportAddress = stringResource(Res.string.sp_email_address)
        val subjectText = stringResource(subject)
        val footer = stringResource(
            Res.string.sp_fb_mail_footer,
            appInfo.versionName,
            appInfo.versionCode.toString(),
            deviceInfo.manufacturer,
            deviceInfo.model,
            deviceInfo.osVersion,
        )

        FeedbackScreen(
            title = stringResource(title),
            intro = stringResource(intro),
            onBack = { nm.back() },
            onSend = { message ->
                nm.back()
                composeMail(
                    MailDraft(
                        to = supportAddress,
                        subject = subjectText,
                        body = "$message\n\n$footer",
                    ),
                )
            },
        )
    }

    /** The stub body, titled by the row that opened it, with back popping the entry. */
    @Composable
    private fun Placeholder(title: StringResource) {
        SupportPlaceholderScreen(title = stringResource(title), onBack = { nm.back() })
    }

    /**
     * The privacy policy: a native summary, with the full documents a tap away.
     *
     * The two outbound rows and the share action are wired to the platform's own link opener
     * and share sheet rather than to a destination — they leave the app, and pretending
     * otherwise with an in-app browser would hide the address of a document whose whole point
     * is being verifiable.
     *
     * All three are null on a build with no backend configured, and the screen then leaves
     * them out. A row that opens nothing is worse on this screen than on any other.
     */
    @Composable
    private fun PrivacyPolicyRoute() {
        val uriHandler = LocalUriHandler.current
        val share = rememberTextSharer()

        val privacyUrl: String? = legalLinks.privacyPolicy.takeIf { it.isNotBlank() }
        val termsUrl: String? = legalLinks.termsOfUse.takeIf { it.isNotBlank() }

        val onShare: (() -> Unit)? = privacyUrl?.let { url -> { share(url) } }
        val onOpenPrivacy: (() -> Unit)? = privacyUrl?.let { url -> { uriHandler.openUri(url) } }
        val onOpenTerms: (() -> Unit)? = termsUrl?.let { url -> { uriHandler.openUri(url) } }

        PrivacyPolicyScreen(
            onBack = { nm.back() },
            onShare = onShare,
            onOpenPrivacy = onOpenPrivacy,
            onOpenTerms = onOpenTerms,
        )
    }
}
