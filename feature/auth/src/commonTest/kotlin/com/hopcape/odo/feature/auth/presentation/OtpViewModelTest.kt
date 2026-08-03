package com.hopcape.odo.feature.auth.presentation

import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.feature.auth.AuthTelemetry
import com.hopcape.odo.feature.auth.domain.OdoSessionManager
import com.hopcape.odo.feature.auth.domain.OtpThrottle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The code screen: verify on the last digit, count wrong tries, and refuse to spray codes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtpViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val phone = PhoneNumber.of("9812345678").getOrNull()!!
    private var clockNow = Instant.parse("2026-08-03T10:00:00Z")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theLastDigitVerifies_withNothingToConfirm() = runTest(dispatcher) {
        val gateway = ScriptedGateway()
        val viewModel = viewModel(gateway)

        viewModel.onEvent(OtpEvent.CodeChanged("12345"))
        advanceTimeBy(SETTLE)
        assertEquals(0, gateway.verifications)

        viewModel.onEvent(OtpEvent.CodeChanged("123456"))
        advanceTimeBy(SETTLE)
        assertEquals(1, gateway.verifications)
    }

    @Test
    fun aWrongCodeCostsATry() = runTest(dispatcher) {
        val viewModel = viewModel(ScriptedGateway(verify = DomainError.InvalidOtp.left()))

        viewModel.onEvent(OtpEvent.CodeChanged("000000"))
        advanceTimeBy(SETTLE)

        assertEquals(OtpUiState.MAX_ATTEMPTS - 1, viewModel.state.value.triesLeft)
        assertTrue(viewModel.state.value.isError)
    }

    @Test
    fun anExpiredCodeCostsNoTry_becauseTheOwnerDidNothingWrong() = runTest(dispatcher) {
        val viewModel = viewModel(ScriptedGateway(verify = DomainError.OtpExpired.left()))

        viewModel.onEvent(OtpEvent.CodeChanged("123456"))
        advanceTimeBy(SETTLE)

        // It costs a resend, not an attempt.
        assertEquals(OtpUiState.MAX_ATTEMPTS, viewModel.state.value.triesLeft)
    }

    @Test
    fun runningOutOfTriesSendsTheOwnerBackToTheNumber() = runTest(dispatcher) {
        val viewModel = viewModel(ScriptedGateway(verify = DomainError.InvalidOtp.left()))
        val effects = mutableListOf<OtpEffect>()
        backgroundScope.collectEffects(viewModel, effects)

        repeat(OtpUiState.MAX_ATTEMPTS) {
            viewModel.onEvent(OtpEvent.CodeChanged(""))
            viewModel.onEvent(OtpEvent.CodeChanged("000000"))
            advanceTimeBy(SETTLE)
        }

        // After three misses the likeliest explanation is the wrong number, not the code.
        assertTrue(effects.contains(OtpEffect.ChangeNumber))
    }

    @Test
    fun resendIsUnavailableUntilTheCooldownLapses() = runTest(dispatcher) {
        val gateway = ScriptedGateway()
        val viewModel = viewModel(gateway)
        advanceTimeBy(SETTLE)

        // The code that opened this screen has already been sent, so the wait starts now —
        // otherwise Resend is live exactly when it is least useful.
        assertFalse(viewModel.state.value.canResend)

        viewModel.onEvent(OtpEvent.ResendClicked)
        advanceTimeBy(SETTLE)
        assertEquals(0, gateway.requests)
    }

    @Test
    fun resendWorksOnceTheCooldownHasPassed() = runTest(dispatcher) {
        val gateway = ScriptedGateway()
        val viewModel = viewModel(gateway)

        clockNow = clockNow.plus(OtpThrottle.COOLDOWN)
        advanceTimeBy(OtpThrottle.COOLDOWN.inWholeMilliseconds + 2_000)

        assertTrue(viewModel.state.value.canResend)
        viewModel.onEvent(OtpEvent.ResendClicked)
        advanceTimeBy(SETTLE)
        assertEquals(1, gateway.requests)
    }

    @Test
    fun aFreshCodeClearsWhateverWasTyped() = runTest(dispatcher) {
        val viewModel = viewModel(ScriptedGateway())
        viewModel.onEvent(OtpEvent.CodeChanged("12345"))

        clockNow = clockNow.plus(OtpThrottle.COOLDOWN)
        advanceTimeBy(OtpThrottle.COOLDOWN.inWholeMilliseconds + 2_000)
        viewModel.onEvent(OtpEvent.ResendClicked)
        advanceTimeBy(SETTLE)

        // A new code invalidates the old one, so anything still on screen is stale.
        assertEquals("", viewModel.state.value.code)
    }

    @Test
    fun aSittingRunsOutOfCodes() = runTest(dispatcher) {
        val gateway = ScriptedGateway()
        val viewModel = viewModel(gateway)

        // One was spent opening the screen; walk the rest of the allowance.
        repeat(OtpThrottle.MAX_REQUESTS) {
            clockNow = clockNow.plus(OtpThrottle.COOLDOWN)
            advanceTimeBy(OtpThrottle.COOLDOWN.inWholeMilliseconds + 2_000)
            viewModel.onEvent(OtpEvent.ResendClicked)
            advanceTimeBy(SETTLE)
        }

        assertTrue(viewModel.state.value.resendExhausted)
        assertFalse(viewModel.state.value.canResend)
        // Each SMS costs money, and a number that has ignored five is not receiving a sixth.
        assertEquals(OtpThrottle.MAX_REQUESTS - 1, gateway.requests)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun viewModel(gateway: AuthGateway) = OtpViewModel(
        phone = phone,
        sessions = OdoSessionManager(gateway, InMemoryStore(), silentTelemetry(), MovingClock()),
        telemetry = silentTelemetry(),
        clock = MovingClock(),
    )

    private fun CoroutineScope.collectEffects(viewModel: OtpViewModel, into: MutableList<OtpEffect>) {
        launch { viewModel.effects.collect { into += it } }
    }

    private class ScriptedGateway(
        private val verify: arrow.core.Either<DomainError, AuthSession> = session().right(),
    ) : AuthGateway {
        var requests = 0
        var verifications = 0

        override suspend fun requestOtp(phone: PhoneNumber) = Unit.right().also { requests++ }
        override suspend fun verifyOtp(phone: PhoneNumber, code: String) = verify.also { verifications++ }
        override suspend fun refresh(refreshToken: String) = session().right()
        override suspend fun signOut(accessToken: String) = Unit.right()
    }

    private inner class MovingClock : Clock {
        override fun now(): Instant = clockNow
    }

    private fun silentTelemetry() = AuthTelemetry(NoopLogger, NoopAnalytics)

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit

        override fun flush() = Unit
    }

    private object NoopAnalytics : AnalyticsTracker {
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }

    private class InMemoryStore : SecureStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String) { values[key] = value }
        override suspend fun get(key: String): String? = values[key]
        override suspend fun remove(key: String) { values.remove(key) }
        override suspend fun clear() = values.clear()
    }

    private companion object {
        const val SETTLE = 2_000L

        fun session() = AuthSession(
            accessToken = "access-1",
            refreshToken = "refresh-1",
            ownerId = OwnerId("user-1"),
            expiresAt = Instant.parse("2026-08-03T11:00:00Z"),
        )
    }
}
