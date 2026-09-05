package com.hopcape.odo.feature.support.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.odo.core.domain.support.SupportContacts
import com.hopcape.odo.core.designsystem.component.ODO_MAX_STARS
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.file.FileTypes
import com.hopcape.odo.core.platform.file.rememberFilePicker
import com.hopcape.odo.core.platform.app.DeviceInfo
import com.hopcape.odo.core.platform.mail.MailDraft
import com.hopcape.odo.core.platform.mail.rememberMailComposer
import com.hopcape.odo.core.platform.store.rememberStoreRater
import com.hopcape.odo.core.platform.share.rememberTextSharer
import com.hopcape.odo.feature.support.domain.RequestDiagnosticsUseCase
import com.hopcape.odo.feature.support.presentation.HelpSupportSheetContent
import com.hopcape.odo.feature.support.presentation.ReportSentScreen
import com.hopcape.odo.feature.support.presentation.IdeaStateSaver
import com.hopcape.odo.feature.support.presentation.ReportStateSaver
import com.hopcape.odo.feature.support.presentation.asMessage
import com.hopcape.odo.feature.support.presentation.flagPriceStateSaver
import com.hopcape.odo.feature.support.presentation.flagprice.DisputedBand
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceEvent
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceScreen
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceUiState
import com.hopcape.odo.feature.support.presentation.idea.IdeaEvent
import com.hopcape.odo.feature.support.presentation.idea.IdeaUiState
import com.hopcape.odo.feature.support.presentation.idea.SuggestIdeaScreen
import com.hopcape.odo.feature.support.presentation.report.ReportEvent
import com.hopcape.odo.feature.support.presentation.report.ReportArea
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceEffect
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceViewModel
import com.hopcape.odo.feature.support.presentation.idea.IdeaEffect
import com.hopcape.odo.feature.support.presentation.idea.SuggestIdeaViewModel
import com.hopcape.odo.feature.support.presentation.report.ReportEffect
import com.hopcape.odo.feature.support.presentation.report.ReportProblemScreen
import com.hopcape.odo.feature.support.presentation.report.ReportProblemViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.hopcape.odo.feature.support.presentation.report.labelResource
import com.hopcape.odo.feature.support.presentation.report.ReportUiState
import com.hopcape.odo.feature.support.presentation.diagnostics.DiagnosticsPrompt
import com.hopcape.odo.feature.support.presentation.diagnostics.DiagnosticsPromptSheets
import com.hopcape.odo.feature.support.presentation.PrivacyPolicyScreen
import com.hopcape.odo.feature.support.presentation.faq.FaqsScreen
import com.hopcape.odo.feature.support.presentation.faq.SupportSearchScreen
import com.hopcape.odo.feature.support.presentation.licences.LicencesScreen
import com.hopcape.odo.feature.support.presentation.rating.RateSheetContent
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_flag_failed
import com.hopcape.odo.feature.support.resources.sp_flag_sent
import com.hopcape.odo.feature.support.resources.sp_idea_failed
import com.hopcape.odo.feature.support.resources.sp_idea_sent
import com.hopcape.odo.feature.support.resources.sp_idea_vote_failed
import com.hopcape.odo.feature.support.resources.sp_sent_attached_logs
import com.hopcape.odo.feature.support.resources.sp_sent_attached_none
import com.hopcape.odo.feature.support.resources.sp_sent_attached_photos
import com.hopcape.odo.feature.support.resources.sp_sent_done
import com.hopcape.odo.feature.support.resources.sp_sent_row_area
import com.hopcape.odo.feature.support.resources.sp_sent_row_attached
import com.hopcape.odo.feature.support.resources.sp_sent_row_ticket
import com.hopcape.odo.feature.support.resources.sp_sent_rp_intro
import com.hopcape.odo.feature.support.resources.sp_sent_rp_title
import com.hopcape.odo.feature.support.resources.sp_sent_wait_action
import com.hopcape.odo.feature.support.resources.sp_sent_wait_body
import com.hopcape.odo.feature.support.resources.sp_sent_wait_label
import com.hopcape.odo.feature.support.resources.sp_email_body
import com.hopcape.odo.feature.support.resources.sp_email_subject
import com.hopcape.odo.feature.support.resources.sp_fb_diagnostics_line
import com.hopcape.odo.feature.support.resources.sp_fb_flag_subject
import com.hopcape.odo.feature.support.resources.sp_fb_idea_subject
import com.hopcape.odo.feature.support.resources.sp_fb_mail_footer
import com.hopcape.odo.feature.support.resources.sp_fb_report_subject
import com.hopcape.odo.feature.support.resources.sp_rate_subject
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
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
    private val requestDiagnostics: RequestDiagnosticsUseCase,
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

            val scope = rememberCoroutineScope()
            // The sheet stays where it is while this is asked and answered: the question is
            // about a row on it, and popping first would make the answer arrive on Profile.
            var prompt: DiagnosticsPrompt by remember { mutableStateOf(DiagnosticsPrompt.Hidden) }

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
                onFlagPriceData = { nm.navigateTo(OdoDestination.Support.FlagPriceData()) },
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
                // "Send diagnostics" (docs/LOGGING_PLAN.md §9): asks first, then queues an
                // upload of whatever is logged so far, regardless of auto-upload consent — an
                // explicit tap here is exactly what D3 (plan §1) means by "manual always
                // available". It asks because the tap sends data off the phone, and it answers
                // with a reference code because an upload nobody can quote is an orphan.
                onSendDiagnostics = { prompt = DiagnosticsPrompt.Asking },
            )

            DiagnosticsPromptSheets(
                prompt = prompt,
                onConfirm = { scope.launch { prompt = DiagnosticsPrompt.Queued(requestDiagnostics()) } },
                onDismiss = { prompt = DiagnosticsPrompt.Hidden },
            )
        }

        entry<OdoDestination.Support.Search> { SupportSearchScreen(onBack = { nm.back() }) }
        entry<OdoDestination.Support.ReportProblem> { ReportProblemRoute() }
        entry<OdoDestination.Support.SuggestIdea> { SuggestIdeaRoute() }
        entry<OdoDestination.Support.FlagPriceData> { key -> FlagPriceRoute(key) }

        // Reached once a report becomes a ticket. A ticket number is the one thing on it that
        // cannot be invented, so nothing navigates here while reports still go out as mail.
        entry<OdoDestination.Support.ReportSent> { key -> ReportSentRoute(key) }
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

    /*
     * The three form routes.
     *
     * Each is a view model now, and each ends in a saved row rather than a mail draft. The
     * mail composer is gone from all three; the one path that still uses it is the "Email us"
     * row on the help sheet, which is the one that asks for email by name.
     */

    @Composable
    private fun ReportProblemRoute() {
        val viewModel = koinViewModel<ReportProblemViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        // Screenshots, so images only — a PDF is not a picture of what went wrong. The name
        // is taken from the reference's last segment, which is what the picker gives us.
        val pickFile = rememberFilePicker(mimeTypes = FileTypes.PHOTOS) { ref ->
            ref?.let { viewModel.onEvent(ReportEvent.AttachmentPicked(it, it.fileName())) }
        }

        CollectEffects(viewModel.effects) { effect ->
            when (effect) {
                ReportEffect.NavigateBack -> nm.back()
                ReportEffect.PickAttachment -> pickFile()
                // The form comes off the stack on the way: coming back to a report that has
                // already been sent, still holding its own text, is how somebody sends it twice.
                is ReportEffect.Sent -> {
                    nm.back()
                    nm.navigateTo(
                        OdoDestination.Support.ReportSent(
                            ticket = effect.reference,
                            area = effect.area,
                            photos = effect.photos,
                            logsAttached = effect.logsAttached,
                            maskedReplyTo = effect.maskedReplyTo,
                        ),
                    )
                }
            }
        }

        ReportProblemScreen(state = state, onEvent = viewModel::onEvent)
    }

    @Composable
    private fun SuggestIdeaRoute() {
        val viewModel = koinViewModel<SuggestIdeaViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val sentMessage = stringResource(Res.string.sp_idea_sent)
        val failedMessage = stringResource(Res.string.sp_idea_failed)
        val voteFailedMessage = stringResource(Res.string.sp_idea_vote_failed)

        CollectEffects(viewModel.effects) { effect ->
            when (effect) {
                IdeaEffect.NavigateBack -> nm.back()
                // No confirmation screen: the list it joins is the answer, and the owner is
                // left on it rather than sent somewhere to press Done.
                IdeaEffect.Sent -> scope.launch { snackbarHostState.showSnackbar(sentMessage) }
                IdeaEffect.Failed -> scope.launch { snackbarHostState.showSnackbar(failedMessage) }
                IdeaEffect.VoteFailed ->
                    scope.launch { snackbarHostState.showSnackbar(voteFailedMessage) }
            }
        }

        SuggestIdeaScreen(
            state = state,
            onEvent = viewModel::onEvent,
            snackbarHostState = snackbarHostState,
        )
    }

    @Composable
    private fun FlagPriceRoute(key: OdoDestination.Support.FlagPriceData) {
        val band = key.band()
        val viewModel = koinViewModel<FlagPriceViewModel> { parametersOf(band) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val pickFile = rememberFilePicker(mimeTypes = FileTypes.PAPERS) { ref ->
            ref?.let { viewModel.onEvent(FlagPriceEvent.BillPicked(it)) }
        }
        val sentMessage = stringResource(Res.string.sp_flag_sent)
        val failedMessage = stringResource(Res.string.sp_flag_failed)

        CollectEffects(viewModel.effects) { effect ->
            when (effect) {
                FlagPriceEffect.NavigateBack -> nm.back()
                FlagPriceEffect.PickBill -> pickFile()
                // Said here rather than on a screen of its own: the footer already promised
                // no answer is coming, so a confirmation screen would be a step for nothing.
                // The owner is left where they are, with the form cleared, and leaves when
                // they are done — a snackbar on a screen being popped is a snackbar nobody
                // sees.
                FlagPriceEffect.Sent -> scope.launch { snackbarHostState.showSnackbar(sentMessage) }
                FlagPriceEffect.Failed ->
                    scope.launch { snackbarHostState.showSnackbar(failedMessage) }
            }
        }

        FlagPriceScreen(
            state = state,
            onEvent = viewModel::onEvent,
            snackbarHostState = snackbarHostState,
        )
    }

    /**
     * The band the key carries, or null when there is none to show.
     *
     * A name without a range is not a band — a caller that knows the job but not what it
     * normally costs would otherwise put "Rs. 0" on the card. That falls through to the
     * branch that asks the owner to name the job, which is the right screen for it.
     */
    private fun OdoDestination.Support.FlagPriceData.band(): DisputedBand? =
        lineName?.takeIf { highPaise > 0L }?.let {
            DisputedBand(
                lineName = it,
                lowPaise = lowPaise,
                highPaise = highPaise,
                city = city,
                workshop = workshop,
                segment = segment,
            )
        }

    @Composable
    private fun ReportSentRoute(key: OdoDestination.Support.ReportSent) {
        ReportSentScreen(
            headline = stringResource(Res.string.sp_sent_rp_title),
            intro = stringResource(Res.string.sp_sent_rp_intro, key.maskedReplyTo),
            facts = listOf(
                stringResource(Res.string.sp_sent_row_ticket) to key.ticket,
                stringResource(Res.string.sp_sent_row_area) to key.areaLabel(),
                stringResource(Res.string.sp_sent_row_attached) to attachedSummary(key),
            ),
            // Only where the report is about a scan. "Enter it manually" is the way round a
            // broken scanner; on a reminders bug it is an instruction to do nothing useful.
            waitLabel = stringResource(Res.string.sp_sent_wait_label).takeIf { key.isScan() },
            waitBody = stringResource(Res.string.sp_sent_wait_body).takeIf { key.isScan() },
            waitAction = stringResource(Res.string.sp_sent_wait_action).takeIf { key.isScan() },
            doneLabel = stringResource(Res.string.sp_sent_done),
            // The sheet that offers manual entry for the car in hand. A direct jump to the
            // form would need a car id, and this screen has no car — it has a ticket.
            onWaitAction = {
                nm.back()
                nm.navigateTo(OdoDestination.Garage.AddToHistory)
            },
            onDone = { nm.back() },
        )
    }

    /**
     * A name for a picked file, from the reference's last segment.
     *
     * Only ever shown on the tile and carried to support as a label. A reference with nothing
     * usable in it falls back to a generic name rather than an empty tile.
     */
    private fun String.fileName(): String =
        substringAfterLast('/').substringBefore('?').ifBlank { FALLBACK_FILE_NAME }

    /** "1 photo · app logs" — what actually travelled, so nobody finds out later. */
    @Composable
    private fun attachedSummary(key: OdoDestination.Support.ReportSent): String {
        val parts = buildList {
            if (key.photos > 0) {
                add(pluralStringResource(Res.plurals.sp_sent_attached_photos, key.photos, key.photos))
            }
            if (key.logsAttached) add(stringResource(Res.string.sp_sent_attached_logs))
        }
        return if (parts.isEmpty()) {
            stringResource(Res.string.sp_sent_attached_none)
        } else {
            parts.joinToString(" · ")
        }
    }

    /** The area, as the report screen words it. */
    @Composable
    private fun OdoDestination.Support.ReportSent.areaLabel(): String =
        stringResource(reportArea().labelResource())

    /** An area the key does not name reads as "something else" rather than as an error. */
    private fun OdoDestination.Support.ReportSent.reportArea(): ReportArea =
        ReportArea.entries.firstOrNull { it.name == area } ?: ReportArea.OTHER

    private fun OdoDestination.Support.ReportSent.isScan(): Boolean =
        reportArea() == ReportArea.BILL_SCAN

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

/** What a picked file is called when its reference names nothing. */
private const val FALLBACK_FILE_NAME = "attachment"
