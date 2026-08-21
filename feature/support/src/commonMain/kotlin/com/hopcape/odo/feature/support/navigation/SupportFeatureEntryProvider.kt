package com.hopcape.odo.feature.support.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.logging.api.LogUploadScheduler
import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.odo.core.domain.support.SupportContacts
import com.hopcape.odo.core.designsystem.component.ODO_MAX_STARS
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
import com.hopcape.odo.core.platform.store.rememberStoreRater
import com.hopcape.odo.core.platform.share.rememberTextSharer
import com.hopcape.odo.feature.support.presentation.HelpSupportSheetContent
import com.hopcape.odo.feature.support.presentation.PrivacyPolicyScreen
import com.hopcape.odo.feature.support.presentation.faq.FaqsScreen
import com.hopcape.odo.feature.support.presentation.faq.SupportSearchScreen
import com.hopcape.odo.feature.support.presentation.feedback.FeedbackScreen
import com.hopcape.odo.feature.support.presentation.licences.LicencesScreen
import com.hopcape.odo.feature.support.presentation.rating.RateSheetContent
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_email
import com.hopcape.odo.feature.support.resources.sp_email_body
import com.hopcape.odo.feature.support.resources.sp_email_subject
import com.hopcape.odo.feature.support.resources.sp_fb_flag_body
import com.hopcape.odo.feature.support.resources.sp_fb_flag_intro
import com.hopcape.odo.feature.support.resources.sp_fb_flag_subject
import com.hopcape.odo.feature.support.resources.sp_fb_idea_body
import com.hopcape.odo.feature.support.resources.sp_fb_idea_intro
import com.hopcape.odo.feature.support.resources.sp_fb_idea_subject
import com.hopcape.odo.feature.support.resources.sp_fb_mail_footer
import com.hopcape.odo.feature.support.resources.sp_fb_report_body
import com.hopcape.odo.feature.support.resources.sp_fb_report_intro
import com.hopcape.odo.feature.support.resources.sp_fb_report_subject
import com.hopcape.odo.feature.support.resources.sp_flag
import com.hopcape.odo.feature.support.resources.sp_idea
import com.hopcape.odo.feature.support.resources.sp_licences
import com.hopcape.odo.feature.support.resources.sp_privacy
import com.hopcape.odo.feature.support.resources.sp_rate
import com.hopcape.odo.feature.support.resources.sp_rate_subject
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
 * Every destination here renders a real screen. Nothing is stubbed, so a row in the sheet
 * can be trusted to lead somewhere — which is the condition on Profile offering the whole
 * feature at all.
 *
 * Email, Rate and Terms have no destination at all. Each ends in a system hand-off — a mail
 * composer, the store listing, a browser — and a navigation key that exists only to bounce
 * straight back out would put an empty screen in the back stack for no reason. They are
 * called from the sheet directly, which is also why the sheet is popped first: returning
 * from the mail app should land on Profile, not on the sheet that opened it.
 */
internal class SupportFeatureEntryProvider(
    private val navigationManager: NavigationManager,
    private val logUploadScheduler: LogUploadScheduler,
    private val appInfo: AppInfo,
    private val deviceInfo: DeviceInfo,
    private val legalLinks: LegalLinks,
    private val supportContacts: SupportContacts,
) : FeatureEntryProvider {

    private val nm get() = navigationManager

    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Support.Help>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) {
            val composeMail = rememberMailComposer()
            val openStoreListing = rememberStoreRater()
            val uriHandler = LocalUriHandler.current
            val supportEmail = supportContacts.email
            val emailSubject = stringResource(Res.string.sp_email_subject)
            val emailBody = stringResource(Res.string.sp_email_body)
            // Blank means the build has no backend configured, and there is no Terms page to
            // open. The chip is left out rather than opening nothing.
            val termsUrl = legalLinks.termsOfUse.takeIf { it.isNotBlank() }

            HelpSupportSheetContent(
                supportEmail = supportEmail,
                // Read from the installed package rather than from BuildInfo's compile-time
                // constants: this line ends up in a support ticket, so it should say what the
                // owner actually has installed.
                versionName = appInfo.versionName,
                versionCode = appInfo.versionCode,
                onClose = { nm.back() },
                onSearch = { nm.navigateTo(OdoDestination.Support.Search) },
                // Straight to the composer on a started draft. There is no screen worth
                // showing between a row that says "Email us" and the mail app, and the
                // draft carries a heading so the box is not empty when it opens.
                onEmail = {
                    nm.back()
                    composeMail(MailDraft(to = supportEmail, subject = emailSubject, body = emailBody))
                },
                onReportProblem = { nm.navigateTo(OdoDestination.Support.ReportProblem) },
                onSuggestIdea = { nm.navigateTo(OdoDestination.Support.SuggestIdea) },
                onFlagPriceData = { nm.navigateTo(OdoDestination.Support.FlagPriceData) },
                // Always offered: the sheet behind it works with or without a store listing,
                // since sending feedback is the half that never depended on one.
                onRate = { nm.navigateTo(OdoDestination.Support.Rate) },
                onFaqs = { nm.navigateTo(OdoDestination.Support.Faqs) },
                // The published document in a browser, like the privacy screen's own Terms
                // row. Deliberately not an in-app web view: hiding the address of a document
                // whose point is being verifiable defeats it.
                onTerms = termsUrl?.let { url -> { uriHandler.openUri(url) } },
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
        entry<OdoDestination.Support.ReportProblem> {
            FeedbackRoute(
                title = Res.string.sp_report,
                intro = Res.string.sp_fb_report_intro,
                subject = Res.string.sp_fb_report_subject,
                body = Res.string.sp_fb_report_body,
            )
        }
        entry<OdoDestination.Support.SuggestIdea> {
            FeedbackRoute(
                title = Res.string.sp_idea,
                intro = Res.string.sp_fb_idea_intro,
                subject = Res.string.sp_fb_idea_subject,
                body = Res.string.sp_fb_idea_body,
            )
        }
        entry<OdoDestination.Support.FlagPriceData> {
            FeedbackRoute(
                title = Res.string.sp_flag,
                intro = Res.string.sp_fb_flag_intro,
                subject = Res.string.sp_fb_flag_subject,
                body = Res.string.sp_fb_flag_body,
            )
        }
        entry<OdoDestination.Support.Rate>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) {
            val composeMail = rememberMailComposer()
            val openStoreListing = rememberStoreRater()
            val supportEmail = supportContacts.email
            val footer = mailFooter()
            // All five subjects up front. The star count is only known inside a click
            // handler, and stringResource cannot be called from one — the range is fixed, so
            // this resolves the same five strings every composition.
            val subjects = (1..ODO_MAX_STARS).map { stringResource(Res.string.sp_rate_subject, it) }

            RateSheetContent(
                onClose = { nm.back() },
                onOpenPlayStore = openStoreListing?.let { open -> { nm.back(); open() } },
                onSendFeedback = { rating, message ->
                    nm.back()
                    composeMail(
                        MailDraft(
                            to = supportEmail,
                            subject = subjects[rating - 1],
                            body = "$message\n\n$footer",
                        ),
                    )
                },
            )
        }

        entry<OdoDestination.Support.Faqs> { FaqsScreen(onBack = { nm.back() }) }
        entry<OdoDestination.Support.Privacy> { PrivacyPolicyRoute() }
        entry<OdoDestination.Support.Licences> {
            val uriHandler = LocalUriHandler.current
            LicencesScreen(
                onBack = { nm.back() },
                onOpenLicence = { url -> uriHandler.openUri(url) },
            )
        }
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
        body: StringResource,
    ) {
        val composeMail = rememberMailComposer()
        val supportAddress = supportContacts.email
        val subjectText = stringResource(subject)
        val footer = mailFooter()

        FeedbackScreen(
            title = stringResource(title),
            intro = stringResource(intro),
            template = stringResource(body),
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

    /**
     * The build and device line every outbound message carries, under the owner's own words.
     *
     * Shared by the feedback forms and the rating sheet so a report and a one-star note
     * carry the same footer. Resolved once per composition rather than per keystroke: it
     * does not depend on what is being typed.
     */
    @Composable
    private fun mailFooter(): String = stringResource(
        Res.string.sp_fb_mail_footer,
        appInfo.versionName,
        appInfo.versionCode.toString(),
        deviceInfo.manufacturer,
        deviceInfo.model,
        deviceInfo.osVersion,
    )

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
