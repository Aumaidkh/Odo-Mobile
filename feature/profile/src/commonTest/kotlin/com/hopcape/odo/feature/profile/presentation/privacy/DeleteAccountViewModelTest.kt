package com.hopcape.odo.feature.profile.presentation.privacy

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AccountEraser
import com.hopcape.odo.core.domain.auth.EraseOutcome
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedAccount
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.profile.domain.usecase.DeleteAccountUseCase
import com.hopcape.odo.feature.profile.domain.usecase.DeleteAllDataUseCase
import com.hopcape.odo.feature.profile.domain.usecase.FakeCarRepository
import com.hopcape.odo.feature.profile.domain.usecase.FakeFileStore
import com.hopcape.odo.feature.profile.domain.usecase.FakeProfileRepository
import com.hopcape.odo.feature.profile.domain.usecase.FakeSettingsRepository
import com.hopcape.odo.feature.profile.presentation.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NUMBER = "9876543210"
private const val CODE = "123456"

/** Records the order the erase steps ran in — the thing this flow is most easily wrong about. */
private class Recorder {
    val steps = mutableListOf<String>()
}

private class FakeEraser(
    private val recorder: Recorder,
    private val result: Either<DomainError, EraseOutcome> = EraseOutcome.DELETED.right(),
) : AccountEraser {
    override suspend fun erase(token: VerifiedPhoneToken): Either<DomainError, EraseOutcome> {
        recorder.steps += "server"
        return result
    }
}

private class FakeVerifiedAccount(
    private val recorder: Recorder,
    private val number: PhoneNumber?,
    private val deleteResult: Either<DomainError, Unit> = Unit.right(),
) : VerifiedAccount {
    override suspend fun verifiedNumber(): PhoneNumber? = number
    override suspend fun delete(): Either<DomainError, Unit> {
        recorder.steps += "firebase"
        return deleteResult
    }
}

private class FakeVerifier(
    private val startResult: Either<DomainError, Unit> = Unit.right(),
    private val submitResult: Either<DomainError, VerifiedPhoneToken> = VerifiedPhoneToken("tok").right(),
) : PhoneVerifier {
    var startCount = 0
        private set
    var forgotten = false
        private set

    override suspend fun startVerification(phone: PhoneNumber): Either<DomainError, Unit> {
        startCount++
        return startResult
    }

    override suspend fun submitCode(code: String): Either<DomainError, VerifiedPhoneToken> = submitResult

    override suspend fun forget() { forgotten = true }
}

class DeleteAccountViewModelTest {

    /**
     * Type the confirmation phrase, then confirm.
     *
     * Every test that reaches past the confirmation goes through this, so the rule is stated
     * once — and a test that forgets it fails on the guard rather than silently deleting.
     */
    private fun DeleteAccountViewModel.confirmWithPhrase() {
        onEvent(DeleteAccountEvent.PhraseChanged(DeleteAccountUiState.CONFIRM_PHRASE))
        onEvent(DeleteAccountEvent.Confirmed)
    }

    private val dispatcher = StandardTestDispatcher()
    private val recorder = Recorder()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun number(): PhoneNumber = PhoneNumber.of(NUMBER).getOrNull()!!

    private fun viewModel(
        signedIn: Boolean = true,
        eraser: AccountEraser = FakeEraser(recorder),
        verifier: FakeVerifier = FakeVerifier(),
        firebaseDelete: Either<DomainError, Unit> = Unit.right(),
        cars: FakeCarRepository = FakeCarRepository(),
        profiles: FakeProfileRepository = FakeProfileRepository(),
        files: FakeFileStore = FakeFileStore(),
    ): DeleteAccountViewModel {
        val account = FakeVerifiedAccount(
            recorder = recorder,
            number = if (signedIn) number() else null,
            deleteResult = firebaseDelete,
        )
        return DeleteAccountViewModel(
            account = account,
            verifier = verifier,
            deleteAccount = DeleteAccountUseCase(
                eraser = eraser,
                account = account,
                deleteAllData = DeleteAllDataUseCase(
                    cars = cars,
                    profiles = profiles,
                    settings = FakeSettingsRepository(),
                    files = files,
                    showcaseSeen = FakeShowcaseSeenStore(),
                ),
                verifier = verifier,
            ),
            telemetry = testTelemetry(),
        )
    }

    @Test
    fun confirming_withoutTypingThePhrase_doesNothing() = runTest {
        val verifier = FakeVerifier()
        val vm = viewModel(verifier = verifier)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(DeleteAccountEvent.Confirmed)
        dispatcher.scheduler.advanceUntilIdle()

        // Guarded in the ViewModel, not only by the button's enabled state. This is the one
        // action in the app that cannot be undone.
        assertEquals(0, verifier.startCount)
        assertEquals(DeleteAccountStep.Confirm, vm.state.value.step)
    }

    @Test
    fun aPartlyTypedPhrase_doesNotUnlockTheButton() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(DeleteAccountEvent.PhraseChanged("Delete my acc"))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.canDelete)
    }

    @Test
    fun thePhrase_isAcceptedInAnyCaseAndWithStraySpaces() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(DeleteAccountEvent.PhraseChanged("  delete my account "))
        dispatcher.scheduler.advanceUntilIdle()

        // The point of typing it is deliberateness, not a spelling test. Refusing lower case
        // would make it a puzzle, and a puzzle is not a confirmation.
        assertTrue(vm.state.value.canDelete)
    }

    @Test
    fun signedIn_showsTheNumberMaskedToItsLastTwoDigits() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        // Enough to recognise, not enough to be worth reading over a shoulder.
        assertEquals("•••• 10", vm.state.value.phoneNumber)
        assertTrue(vm.state.value.hasAccount)
    }

    @Test
    fun neverSignedIn_hasNoNumberAndSkipsTheCodeEntirely() = runTest {
        val verifier = FakeVerifier()
        val vm = viewModel(signedIn = false, verifier = verifier)
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()

        // Nobody should have to prove a number to delete a database that only ever lived on
        // their own phone.
        assertEquals(0, verifier.startCount)
        assertNull(vm.state.value.phoneNumber)
    }

    @Test
    fun neverSignedIn_wipesLocallyAndFinishes() = runTest {
        val cars = FakeCarRepository()
        val vm = viewModel(signedIn = false, cars = cars)
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmWithPhrase()
        val effect = vm.effects.first()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DeleteAccountEffect.Deleted, effect)
        assertEquals(1, cars.softDeleted.size)
        // The server was never asked, because there is nothing there to ask about.
        assertTrue(recorder.steps.isEmpty())
    }

    @Test
    fun signedIn_confirm_sendsACodeAndMovesToVerify() = runTest {
        val verifier = FakeVerifier()
        val vm = viewModel(verifier = verifier)
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, verifier.startCount)
        assertEquals(DeleteAccountStep.Verify, vm.state.value.step)
    }

    @Test
    fun erase_runsServerThenFirebaseThenLocal() = runTest {
        val cars = FakeCarRepository()
        val vm = viewModel(cars = cars)
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged(CODE))

        vm.onEvent(DeleteAccountEvent.CodeSubmitted)
        val effect = vm.effects.first()
        dispatcher.scheduler.advanceUntilIdle()

        // Outermost first, so a failure at any step leaves everything after it untouched.
        assertEquals(listOf("server", "firebase"), recorder.steps)
        assertEquals(1, cars.softDeleted.size)
        assertEquals(DeleteAccountEffect.Deleted, effect)
    }

    @Test
    fun serverRefuses_leavesLocalDataAlone() = runTest {
        val cars = FakeCarRepository()
        val vm = viewModel(
            eraser = FakeEraser(recorder, DomainError.AccountEraseFailed("erase_failed").left()),
            cars = cars,
        )
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged(CODE))

        vm.onEvent(DeleteAccountEvent.CodeSubmitted)
        dispatcher.scheduler.advanceUntilIdle()

        // Nothing anywhere has changed, so the owner can try again — which is exactly why
        // the server goes first.
        assertEquals(listOf("server"), recorder.steps)
        assertTrue(cars.softDeleted.isEmpty())
        assertEquals(DeleteAccountStep.Verify, vm.state.value.step)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun firebaseRefuses_leavesLocalDataAlone() = runTest {
        val cars = FakeCarRepository()
        val vm = viewModel(
            firebaseDelete = DomainError.ReVerificationRequired.left(),
            cars = cars,
        )
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged(CODE))

        vm.onEvent(DeleteAccountEvent.CodeSubmitted)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("server", "firebase"), recorder.steps)
        assertTrue(cars.softDeleted.isEmpty())
        // A fresh code, not a retyped one.
        assertEquals(DeleteAccountStep.Confirm, vm.state.value.step)
    }

    @Test
    fun localWipeFails_saysTheAccountIsGoneAndOffersOnlyTheWipe() = runTest {
        // The wipe fails on the profile delete, after the server and Firebase both succeeded.
        val vm = viewModel(profiles = FakeProfileRepository(deleteFailing = true))
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged(CODE))

        vm.onEvent(DeleteAccountEvent.CodeSubmitted)
        dispatcher.scheduler.advanceUntilIdle()

        // Neither success nor plain failure: retrying the erase would reach a server with
        // nothing left on it.
        assertEquals(DeleteAccountStep.LocalWipeFailed, vm.state.value.step)
    }

    @Test
    fun wrongCode_staysOnVerifyAndNeverReachesTheServer() = runTest {
        val vm = viewModel(verifier = FakeVerifier(submitResult = DomainError.InvalidOtp.left()))
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged(CODE))

        vm.onEvent(DeleteAccountEvent.CodeSubmitted)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(recorder.steps.isEmpty())
        assertEquals(DeleteAccountStep.Verify, vm.state.value.step)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun resend_clearsTheDigitsTheOldCodeWasFor() = runTest {
        val verifier = FakeVerifier()
        val vm = viewModel(verifier = verifier)
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged("999"))

        vm.onEvent(DeleteAccountEvent.ResendRequested)
        dispatcher.scheduler.advanceUntilIdle()

        // Leaving them would invite submitting a code that can no longer work.
        assertEquals("", vm.state.value.code)
        assertEquals(2, verifier.startCount)
    }

    @Test
    fun incompleteCode_doesNothing() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged("123"))

        vm.onEvent(DeleteAccountEvent.CodeSubmitted)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(recorder.steps.isEmpty())
        assertEquals(DeleteAccountStep.Verify, vm.state.value.step)
    }

    @Test
    fun success_forgetsTheProviderSignIn() = runTest {
        val verifier = FakeVerifier()
        val vm = viewModel(verifier = verifier)
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged(CODE))

        vm.onEvent(DeleteAccountEvent.CodeSubmitted)
        vm.effects.first()
        dispatcher.scheduler.advanceUntilIdle()

        // Otherwise the next person to open the app on this phone is already verified as the
        // owner who just deleted their account.
        assertTrue(verifier.forgotten)
    }

    @Test
    fun noAccountOnTheServer_stillWipesLocallyAndSucceeds() = runTest {
        val cars = FakeCarRepository()
        val vm = viewModel(
            eraser = FakeEraser(recorder, EraseOutcome.NO_ACCOUNT.right()),
            cars = cars,
        )
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmWithPhrase()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(DeleteAccountEvent.CodeChanged(CODE))

        vm.onEvent(DeleteAccountEvent.CodeSubmitted)
        val effect = vm.effects.first()
        dispatcher.scheduler.advanceUntilIdle()

        // The number was proved and the server has nothing under it. Not a failure — the
        // owner asked for their data gone and it is.
        assertEquals(DeleteAccountEffect.Deleted, effect)
        assertEquals(1, cars.softDeleted.size)
    }
    private class FakeShowcaseSeenStore : ShowcaseSeenStore {
        val seen = mutableSetOf<ShowcaseHookId>()
        override suspend fun isSeen(hook: ShowcaseHookId): Boolean = hook in seen
        override suspend fun markSeen(hook: ShowcaseHookId) {
            seen += hook
        }

        override suspend fun clearAll() = seen.clear()
    }

}
