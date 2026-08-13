package com.hopcape.odo.core.platform.permission

import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two decisions behind a permission button that has to do something visible: whether a
 * permission can be asked for on this phone at all, and whether the answer that came back
 * was ever put in front of the owner.
 *
 * Both used to be assumed rather than decided, and both assumptions were wrong in a way that
 * made the "Allow notifications" button on the document success screen do nothing (#131).
 */
class PermissionStatusTest {

    @Test
    fun `notifications are not a runtime permission before Android 13`() {
        // The permission does not exist down here, so nothing can grant it and no dialog can
        // be shown for it. Asking is a refusal with an empty screen.
        assertFalse(isRuntimePermission(PlatformPermission.POST_NOTIFICATIONS, Build.VERSION_CODES.O))
        assertFalse(isRuntimePermission(PlatformPermission.POST_NOTIFICATIONS, Build.VERSION_CODES.S_V2))
    }

    @Test
    fun `notifications become a runtime permission from Android 13`() {
        assertTrue(isRuntimePermission(PlatformPermission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU))
        assertTrue(isRuntimePermission(PlatformPermission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU + 3))
    }

    @Test
    fun `every other permission is a runtime permission on every version Odo supports`() {
        // minSdk 26. Nothing else in PlatformPermission arrived later than that.
        listOf(
            PlatformPermission.CAMERA,
            PlatformPermission.ACCESS_FINE_LOCATION,
            PlatformPermission.BLUETOOTH_CONNECT,
            PlatformPermission.ACTIVITY_RECOGNITION,
            PlatformPermission.ACCESS_BACKGROUND_LOCATION,
        ).forEach { permission ->
            assertTrue(isRuntimePermission(permission, Build.VERSION_CODES.O), permission)
        }
    }

    @Test
    fun `notifications with no permission to grant are read off the settings switch`() {
        // On by default, so the prompt to allow them never appears...
        assertEquals(PermissionStatus.Granted, notificationStatus(enabled = true))
        // ...and once switched off there is no dialog that turns them back on, only settings.
        assertEquals(PermissionStatus.Blocked, notificationStatus(enabled = false))
    }

    @Test
    fun `an instant refusal on the first ask means no dialog was shown`() {
        assertTrue(
            answeredWithoutAsking(
                granted = false,
                askedBefore = false,
                statusAfterAnswer = PermissionStatus.Blocked,
                elapsedMs = 12,
            ),
        )
    }

    @Test
    fun `a dialog the owner dismissed is not mistaken for one that never appeared`() {
        // Back-dismissing is not a denial, so it leaves the permission looking blocked on a
        // first ask too. The time it took is the only thing that separates the two.
        assertFalse(
            answeredWithoutAsking(
                granted = false,
                askedBefore = false,
                statusAfterAnswer = PermissionStatus.Blocked,
                elapsedMs = 4_000,
            ),
        )
    }

    @Test
    fun `a permission that was asked for before is left to the screen to handle`() {
        // The status is already Blocked before the tap, so the screen offers settings itself
        // and never gets here.
        assertFalse(
            answeredWithoutAsking(
                granted = false,
                askedBefore = true,
                statusAfterAnswer = PermissionStatus.Blocked,
                elapsedMs = 12,
            ),
        )
    }

    @Test
    fun `a real denial leaves the permission askable and needs no settings trip`() {
        // From Android 11 the first "Deny" still allows one more ask.
        assertFalse(
            answeredWithoutAsking(
                granted = false,
                askedBefore = false,
                statusAfterAnswer = PermissionStatus.Askable,
                elapsedMs = 12,
            ),
        )
    }

    @Test
    fun `a grant is never a refusal, however fast it arrives`() {
        // An already-granted permission answers synchronously.
        assertFalse(
            answeredWithoutAsking(
                granted = true,
                askedBefore = false,
                statusAfterAnswer = PermissionStatus.Granted,
                elapsedMs = 0,
            ),
        )
    }
}
