package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.platform.app.CurrentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The two things underneath sign-in that only a running app can prove.
 *
 * [AuthEndToEndTest] fakes the gateway, so everything checked here is invisible to it. Neither
 * of these fails at build time and neither fails until someone taps Continue on the number
 * screen — at which point it is a crash or a silent refusal, in front of an owner.
 *
 * What is still not covered: the Firebase round trip itself. That needs a test number
 * configured in the Firebase console (see `infrastructure/firebase/auth/README.md`) and
 * `supabase.phoneAuth=true`, and it spends money on any number that is not one.
 */
@RunWith(AndroidJUnit4::class)
class PhoneAuthSeamTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Firebase's `verifyPhoneNumber` has no Activity-free overload, and this holder is the
     * only way anything outside composition can get one. It starts tracking in its
     * constructor and is built at start for that reason — resolved lazily it would begin
     * listening after the Activity it is being asked about already resumed, and answer null
     * forever.
     */
    @Test
    fun theActivityHolderAnswersWhileAnActivityIsUp() {
        rule.waitForIdle()

        val activity = GlobalContext.get().get<CurrentActivity>().get()

        assertNotNull("No Activity tracked while MainActivity is resumed", activity)
        assertEquals(MainActivity::class.java, activity!!::class.java)
    }

    /**
     * `firebaseAuthModule` binds a verifier that refuses to send anything, and the Android
     * bootstrap is what replaces it. Nothing about that ordering is checked by the compiler:
     * drop the `includes(firebaseAuthAndroidModule)` line and the app still builds, still
     * starts, and every sign-in quietly reports that no code could be sent.
     *
     * Asserted by name because both implementations are `internal` to their module, so
     * neither type is nameable from here.
     */
    @Test
    fun theRealFirebaseVerifierIsTheOneBound() {
        val verifier = GlobalContext.get().get<PhoneVerifier>()

        assertEquals(
            "The Android bootstrap did not override the unavailable verifier",
            "FirebasePhoneVerifier",
            verifier::class.simpleName,
        )
    }
}
