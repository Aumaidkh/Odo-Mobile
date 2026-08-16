package com.hopcape.odo.feature.refuel.presentation.autodetect

import com.hopcape.odo.core.platform.permission.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The order the opt-in asks for its two permissions, and what the button does at each point.
 *
 * Worth its own suite because this screen previously ran one button labelled "Turn on
 * auto-detect" straight into a system permission page, having never asked for
 * `POST_NOTIFICATIONS` at all. Both halves of that were wrong: the label named something the
 * tap did not do, and the permission that lets a detected fill reach the owner was never
 * requested. The step is what both the label and the tap are now derived from, so a wrong
 * step is a screen that lies again.
 */
class AutoDetectSetupStepTest {

    private fun state(
        notify: PermissionStatus = PermissionStatus.Askable,
        accessGranted: Boolean = false,
        optedIn: Boolean = false,
        needsAutostart: Boolean = false,
    ) = AutoDetectUiState(
        loading = false,
        optedIn = optedIn,
        accessGranted = accessGranted,
        notifyStatus = notify,
        needsAutostart = needsAutostart,
    )

    @Test
    fun aFreshPhoneIsAskedForTheSmallerPermissionFirst() {
        // Notifications before notification access, deliberately. The second ask's own system
        // dialog warns that Odo will be able to read every notification, and arriving at it
        // having already granted the first is what lets the screen explain the difference.
        assertEquals(AutoDetectSetupStep.PostNotifications, state().setupStep)
    }

    @Test
    fun grantingNotificationsMovesOnToNotificationAccess() {
        val step = state(notify = PermissionStatus.Granted).setupStep

        assertEquals(AutoDetectSetupStep.NotificationAccess, step)
    }

    @Test
    fun onlyBothPermissionsTogetherReachTheSwitch() {
        val step = state(notify = PermissionStatus.Granted, accessGranted = true).setupStep

        assertEquals(AutoDetectSetupStep.Ready, step)
    }

    @Test
    fun notificationAccessAloneStillOwesTheFirstPermission() {
        // The order the owner can land in by granting access from system settings before ever
        // opening this screen. Detection would run and its draft would go nowhere.
        val step = state(notify = PermissionStatus.Askable, accessGranted = true).setupStep

        assertEquals(AutoDetectSetupStep.PostNotifications, step)
    }

    @Test
    fun aPermanentlyDeniedNotificationsPermissionStaysOnItsStep() {
        // Blocked is not granted, so the chain does not advance; what changes is that the
        // button has to open app settings, because the system will not show its dialog again.
        val blocked = state(notify = PermissionStatus.Blocked)

        assertEquals(AutoDetectSetupStep.PostNotifications, blocked.setupStep)
        assertTrue(blocked.notifyBlocked)
    }

    @Test
    fun anAskableNotificationsPermissionIsNotTreatedAsBlocked() {
        assertFalse(state(notify = PermissionStatus.Askable).notifyBlocked)
    }

    @Test
    fun theFlowOnlyLeavesThePermissionsPageOnceBothAreGranted() {
        // permissionsSettled is what the CTA branches on: before it, the button still has an
        // Android permission to ask for and must not advance the page.
        assertFalse(state().permissionsSettled)
        assertFalse(state(notify = PermissionStatus.Granted).permissionsSettled)
        assertFalse(state(accessGranted = true).permissionsSettled)
        assertTrue(state(notify = PermissionStatus.Granted, accessGranted = true).permissionsSettled)
    }

    @Test
    fun autostartNeverGatesTheButton() {
        // No API reports whether the manufacturer's switch is on, so a step that waited for it
        // could never clear. Detection does work until the phone next reclaims the app, which
        // makes autostart advice rather than a gate.
        val step = state(
            notify = PermissionStatus.Granted,
            accessGranted = true,
            needsAutostart = true,
        ).setupStep

        assertEquals(AutoDetectSetupStep.Ready, step)
    }

    @Test
    fun bothRevocationWarningsOnlyApplyOnceDetectionIsOn() {
        // On the opt-in nothing is switched on yet, so neither is a warning about anything —
        // they are the setup chain, which the checklist already shows.
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
        val working = state(notify = PermissionStatus.Granted, accessGranted = true, optedIn = true)

        assertFalse(working.needsAccess)
        assertFalse(working.needsNotifyPermission)
        assertFalse(working.showAutostart)
    }

    @Test
    fun autostartAdviceOnlyShowsOnceDetectionIsOn() {
        // This is the card in the *settings* body. Before opt-in the same advice is a page of
        // its own in the flow, so a card here as well would say it twice.
        assertFalse(state(needsAutostart = true).showAutostart)
        assertTrue(state(needsAutostart = true, optedIn = true).showAutostart)
    }
}
