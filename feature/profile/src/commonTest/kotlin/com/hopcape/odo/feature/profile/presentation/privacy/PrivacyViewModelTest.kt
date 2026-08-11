package com.hopcape.odo.feature.profile.presentation.privacy

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.PrivacyPreferences
import com.hopcape.odo.feature.profile.domain.usecase.FakeProfileRepository
import com.hopcape.odo.feature.profile.domain.usecase.FakeSettingsRepository
import com.hopcape.odo.feature.profile.domain.usecase.FakeTripRepository
import com.hopcape.odo.feature.profile.domain.usecase.UpdatePrivacyUseCase
import com.hopcape.odo.feature.profile.domain.usecase.testProfile
import com.hopcape.odo.feature.profile.presentation.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records the consent gate so a test can prove the switch reaches it, not just the DB. */
private class RecordingConsent : AnalyticsTracker {
    val consents = mutableListOf<ConsentStatus>()
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) = Unit
    override fun setConsent(status: ConsentStatus) { consents += status }
    override fun flush() = Unit
}

class PrivacyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        profiles: FakeProfileRepository = FakeProfileRepository(),
        consent: AnalyticsTracker = RecordingConsent(),
        trips: FakeTripRepository = FakeTripRepository(),
    ) = PrivacyViewModel(
        settings = settings,
        profiles = profiles,
        updatePrivacy = UpdatePrivacyUseCase(settings = settings, profiles = profiles, trips = trips),
        analytics = consent,
        telemetry = testTelemetry(),
    )

    @Test
    fun keepTripRoutes_off_erasesTheRoutesAlreadyStored() = runTest {
        val settings = FakeSettingsRepository(
            AppSettings.Default.copy(privacy = PrivacyPreferences(keepTripRoutes = true)),
        )
        val trips = FakeTripRepository()
        val vm = viewModel(settings, trips = trips)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(PrivacyEvent.KeepTripRoutesToggled(false))
        dispatcher.scheduler.advanceUntilIdle()

        // "Only distance is stored" has to be true of the trips already on the phone, or
        // the switch is a promise about the future rather than a privacy control.
        assertEquals(1, trips.forgetCount)
    }

    @Test
    fun keepTripRoutes_on_erasesNothing() = runTest {
        val trips = FakeTripRepository()
        val vm = viewModel(trips = trips)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(PrivacyEvent.KeepTripRoutesToggled(true))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, trips.forgetCount)
    }

    @Test
    fun keepTripRoutes_off_failedSettingWrite_erasesNothing() = runTest {
        val settings = FakeSettingsRepository(failing = true)
        val trips = FakeTripRepository()
        val vm = viewModel(settings, trips = trips)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(PrivacyEvent.KeepTripRoutesToggled(false))
        dispatcher.scheduler.advanceUntilIdle()

        // Erasing first and then failing to store the switch would leave the owner's routes
        // gone with the switch still showing on — the one outcome that cannot be undone or
        // explained.
        assertEquals(0, trips.forgetCount)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun state_startsFromWhatIsStoredInBothPlaces() = runTest {
        val settings = FakeSettingsRepository(
            AppSettings.Default.copy(
                privacy = PrivacyPreferences(keepTripRoutes = true, usageAnalytics = false),
            ),
        )
        val profiles = FakeProfileRepository(testProfile().withPriceSharing(false))

        val state = viewModel(settings, profiles).state
        dispatcher.scheduler.advanceUntilIdle()

        // Three switches, two sources. A screen reading only settings would show price
        // sharing on for an owner who turned it off.
        assertTrue(state.value.keepTripRoutes)
        assertFalse(state.value.usageAnalytics)
        assertFalse(state.value.sharePrices)
    }

    @Test
    fun keepTripRoutes_writesToSettings() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(PrivacyEvent.KeepTripRoutesToggled(true))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(settings.stored.value.privacy.keepTripRoutes)
        assertTrue(vm.state.value.keepTripRoutes)
    }

    @Test
    fun sharePrices_writesToTheProfileNotTheSettings() = runTest {
        val settings = FakeSettingsRepository()
        val profiles = FakeProfileRepository()
        val vm = viewModel(settings, profiles)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(PrivacyEvent.SharePricesToggled(false))
        dispatcher.scheduler.advanceUntilIdle()

        // The profile is what syncs, which is the whole reason this switch lives there.
        assertEquals(false, profiles.stored.value?.sharesPricesAnonymously)
        assertFalse(vm.state.value.sharePrices)
    }

    @Test
    fun usageAnalytics_off_stopsTrackingImmediately() = runTest {
        val consent = RecordingConsent()
        val vm = viewModel(consent = consent)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(PrivacyEvent.UsageAnalyticsToggled(false))
        dispatcher.scheduler.advanceUntilIdle()

        // Not at the next launch. An owner who opts out must not keep being counted for the
        // rest of the session.
        assertEquals(listOf(ConsentStatus.DENIED), consent.consents)
    }

    @Test
    fun usageAnalytics_failedWrite_leavesTheGateAlone() = runTest {
        val settings = FakeSettingsRepository(failing = true)
        val consent = RecordingConsent()
        val vm = viewModel(settings, consent = consent)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(PrivacyEvent.UsageAnalyticsToggled(false))
        dispatcher.scheduler.advanceUntilIdle()

        // The switch still shows the stored answer, so applying the gate anyway would leave
        // the app behaving one way and reporting the other.
        assertTrue(consent.consents.isEmpty())
        assertTrue(vm.state.value.usageAnalytics)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun failedWrite_springsTheSwitchBack() = runTest {
        val settings = FakeSettingsRepository(failing = true)
        val vm = viewModel(settings)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(PrivacyEvent.KeepTripRoutesToggled(true))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.keepTripRoutes, "state follows what is stored, not the tap")
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun aSecondToggle_clearsThePreviousError() = runTest {
        val settings = FakeSettingsRepository(failing = true)
        val vm = viewModel(settings)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(PrivacyEvent.KeepTripRoutesToggled(true))
        dispatcher.scheduler.advanceUntilIdle()

        settings.failing = false
        vm.onEvent(PrivacyEvent.KeepTripRoutesToggled(true))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.keepTripRoutes)
    }

    @Test
    fun noProfileYet_showsPriceSharingOnRatherThanBlank() = runTest {
        val vm = viewModel(profiles = FakeProfileRepository(profile = null))
        dispatcher.scheduler.advanceUntilIdle()

        // Before onboarding writes a row the switch still has a real answer, and it is the
        // same one a fresh profile gets.
        assertTrue(vm.state.value.sharePrices)
    }
}
