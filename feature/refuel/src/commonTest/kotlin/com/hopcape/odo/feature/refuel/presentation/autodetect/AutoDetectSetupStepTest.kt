package com.hopcape.odo.feature.refuel.presentation.autodetect

import com.hopcape.odo.core.platform.permission.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which asks the opt-in makes, in what order, and what its counter says while it does.
 *
 * Worth its own suite because this screen previously ran one button labelled "Turn on
 * auto-detect" straight into a system permission page, having never asked for
 * `POST_NOTIFICATIONS` at all. Both halves of that were wrong: the label named something the tap
 * did not do, and the permission that lets a detected fill reach the owner was never requested.
 * The flow is now derived from the page and the step list, so a wrong step is a screen that
 * lies again. `POST_NOTIFICATIONS` is no longer one of the numbered steps — it is raised as a
 * dialog on the way out of the pitch page — so what these check is that it neither gates the
 * flow nor counts towards it, while still being reported once detection is on.
 */
class AutoDetectSetupStepTest {

    private fun state(
        notify: PermissionStatus = PermissionStatus.Askable,
        accessGranted: Boolean = false,
        optedIn: Boolean = false,
        autostartAcknowledged: Boolean = false,
        needsAutostart: Boolean = false,
        page: AutoDetectPage = AutoDetectPage.Why,
        steps: List<AutoDetectStep> = emptyList(),
    ) = AutoDetectUiState(
        loading = false,
        optedIn = optedIn,
        accessGranted = accessGranted,
        notifyStatus = notify,
        autostartAcknowledged = autostartAcknowledged,
        needsAutostart = needsAutostart,
        page = page,
        steps = steps,
    )

    private val bothSteps = listOf(AutoDetectStep.Access, AutoDetectStep.Background)

    @Test
    fun aFreshPhoneOwesBothAsks() {
        // Access first, background last: background is the only one that changes nothing today,
        // so making it wait costs the owner nothing and putting it first would.
        assertEquals(bothSteps, state().pendingSteps)
    }

    @Test
    fun anAlreadyGrantedPermissionIsNotAskedFor() {
        val pending = state(accessGranted = true).pendingSteps

        assertEquals(listOf(AutoDetectStep.Background), pending)
    }

    @Test
    fun theNotificationsPermissionIsNeverOneOfTheSteps() {
        // It is a one-tap dialog raised on the way out of the pitch page, and the drawn
        // notification there is its whole explanation. Counting it made the flow read as one ask
        // longer than it is, and a screen repeating the drawing said the same thing twice.
        val ungranted = state(notify = PermissionStatus.Askable)

        assertEquals(bothSteps, ungranted.pendingSteps)
        assertEquals(bothSteps, state(notify = PermissionStatus.Granted).pendingSteps)
    }

    @Test
    fun theDialogIsOnlyRaisedWhenTheSystemWouldActuallyShowIt() {
        // Blocked means it will not, and turning "see what it needs" into a trip to app settings
        // is not a fair reading of that button. The settings body says so plainly instead.
        assertTrue(state(notify = PermissionStatus.Askable).notifyAskPending)
        assertFalse(state(notify = PermissionStatus.Blocked).notifyAskPending)
        assertFalse(state(notify = PermissionStatus.Granted).notifyAskPending)
    }

    @Test
    fun anOwnerWhoOwesNothingHasNoStepsAtAll() {
        // The state an owner reaches by granting access elsewhere and acknowledging the
        // background setting. The pitch page's button turns detection on rather than walking
        // them through screens that would all be answered already.
        val settled = state(
            notify = PermissionStatus.Granted,
            accessGranted = true,
            autostartAcknowledged = true,
        )

        assertTrue(settled.pendingSteps.isEmpty())
    }

    @Test
    fun theBackgroundAskIsOwedUntilTheOwnerSaysOtherwise() {
        // Nothing can read that setting, so acknowledgement is the only signal there is. It is
        // asked for on every phone, because every phone sleeps apps to save battery.
        assertTrue(AutoDetectStep.Background in state().pendingSteps)
        assertFalse(AutoDetectStep.Background in state(autostartAcknowledged = true).pendingSteps)
    }

    @Test
    fun theCounterNumbersThePageWithinTheStepsThisRunWillMake() {
        val onAccess = state(page = AutoDetectPage.Access, steps = bothSteps)

        assertEquals(1, onAccess.stepNumber)
        assertEquals(2, onAccess.stepTotal)
    }

    @Test
    fun aHandoffPageCountsAsTheStepItBelongsTo() {
        // The forewarning about the system page is not an ask of its own, and numbering it as
        // one would make the flow look longer every time it explained itself.
        val handoff = state(page = AutoDetectPage.AccessHandoff, steps = bothSteps)

        assertEquals(1, handoff.stepNumber)
        assertEquals(2, handoff.stepTotal)
    }

    @Test
    fun theCounterIsOutOfWhatThisRunActuallyAsksFor() {
        // A phone that already holds notification access sees one step, not two of which one is
        // skipped. Counting asks that will not happen makes the flow read as longer than it is.
        val steps = listOf(AutoDetectStep.Background)
        val onBackground = state(page = AutoDetectPage.Background, steps = steps)

        assertEquals(1, onBackground.stepNumber)
        assertEquals(1, onBackground.stepTotal)
    }

    @Test
    fun thePitchPageHasNoCounter() {
        assertEquals(0, state(page = AutoDetectPage.Why, steps = bothSteps).stepTotal)
    }

    @Test
    fun backFromTheFirstPageLeavesTheScreen() {
        assertNull(state(page = AutoDetectPage.Why).previousPage)
    }

    @Test
    fun backWalksTheFlowOnePageAtATime() {
        assertEquals(
            AutoDetectPage.Access,
            state(page = AutoDetectPage.AccessHandoff, steps = bothSteps).previousPage,
        )
        assertEquals(
            AutoDetectPage.AccessHandoff,
            state(page = AutoDetectPage.Background, steps = bothSteps).previousPage,
        )
        assertEquals(
            AutoDetectPage.Background,
            state(page = AutoDetectPage.BackgroundHandoff, steps = bothSteps).previousPage,
        )
        assertEquals(
            AutoDetectPage.Why,
            state(page = AutoDetectPage.Access, steps = bothSteps).previousPage,
        )
    }

    @Test
    fun backNeverReversesOntoAnAskThisRunSkipped() {
        // The owner's phone held notification access already, so there is no step-one page
        // behind them. Reversing onto one would ask for a permission they already hold.
        val steps = listOf(AutoDetectStep.Background)

        assertEquals(
            AutoDetectPage.Why,
            state(page = AutoDetectPage.Background, steps = steps).previousPage,
        )
    }

    @Test
    fun eachSatisfiedStepLeadsToTheNextOne() {
        val mid = state(page = AutoDetectPage.Access, steps = bothSteps)

        assertEquals(AutoDetectPage.Background, mid.pageAfter(AutoDetectStep.Access))
    }

    @Test
    fun theLastStepLeadsNowhere() {
        // Null is what turns detection on. Anything else would leave the owner one screen short
        // of the feature they just granted everything for.
        assertNull(state(steps = bothSteps).pageAfter(AutoDetectStep.Background))
    }

    @Test
    fun aStepThatIsNotInThisRunLeadsNowhere() {
        val steps = listOf(AutoDetectStep.Background)

        assertNull(state(steps = steps).pageAfter(AutoDetectStep.Access))
    }

    @Test
    fun aPermanentlyDeniedNotificationsPermissionIsRecognisedAsSuch() {
        // Blocked changes what the settings-body button has to do: open app settings, because
        // the system will not show its dialog again.
        assertTrue(state(notify = PermissionStatus.Blocked).notifyBlocked)
    }

    @Test
    fun anAskableNotificationsPermissionIsNotTreatedAsBlocked() {
        assertFalse(state(notify = PermissionStatus.Askable).notifyBlocked)
    }

    @Test
    fun bothRevocationWarningsOnlyApplyOnceDetectionIsOn() {
        // On the opt-in nothing is switched on yet, so neither is a warning about anything —
        // they are the asks the flow is in the middle of making.
        val optedOut = state()

        assertFalse(optedOut.needsAccess)
        assertFalse(optedOut.needsNotifyPermission)
    }

    @Test
    fun aRevokedNotificationPermissionIsCalledOutAfterOptIn() {
        // The owner turns notifications off for Odo months later, from the system's own UI.
        // Every switch on this screen still reads "on" and no detected fill ever reaches them.
        val revoked = state(notify = PermissionStatus.Blocked, accessGranted = true, optedIn = true)

        assertTrue(revoked.needsNotifyPermission)
        assertFalse(revoked.needsAccess)
    }

    @Test
    fun aRevokedNotificationAccessIsCalledOutAfterOptIn() {
        val revoked = state(notify = PermissionStatus.Granted, accessGranted = false, optedIn = true)

        assertTrue(revoked.needsAccess)
        assertFalse(revoked.needsNotifyPermission)
    }

    @Test
    fun aFullyWorkingSetupWarnsAboutNothing() {
        val working = state(
            notify = PermissionStatus.Granted,
            accessGranted = true,
            optedIn = true,
            autostartAcknowledged = true,
        )

        assertFalse(working.needsAccess)
        assertFalse(working.needsNotifyPermission)
        assertFalse(working.showAutostart)
    }

    @Test
    fun theStandingAutostartReminderOnlyShowsOnceDetectionIsOn() {
        // This is the card in the *settings* body, and only on the skins that refuse background
        // starts outright. Before opt-in the same advice is a page of its own in the flow, so a
        // card here as well would say it twice.
        assertFalse(state(needsAutostart = true).showAutostart)
        assertTrue(state(needsAutostart = true, optedIn = true).showAutostart)
    }
}
