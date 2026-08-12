package com.hopcape.odo.feature.profile.presentation

import kotlin.time.Instant
import kotlinx.coroutines.flow.flowOf
import com.hopcape.odo.core.common.BuildInfo
import com.hopcape.odo.core.domain.sync.SyncStatusProvider
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.owner.model.OwnerEmail
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.feature.profile.domain.usecase.DeleteAllDataUseCase
import com.hopcape.odo.feature.profile.domain.usecase.FakeCarRepository
import com.hopcape.odo.feature.profile.domain.usecase.FakeFileStore
import com.hopcape.odo.feature.profile.domain.usecase.FakeProfileRepository
import com.hopcape.odo.feature.profile.domain.usecase.FakeSettingsRepository
import com.hopcape.odo.feature.profile.domain.usecase.ObserveProfileUseCase
import com.hopcape.odo.feature.profile.domain.usecase.SetAvatarUseCase
import com.hopcape.odo.feature.profile.domain.usecase.UpdateOwnerDetailsUseCase
import com.hopcape.odo.feature.profile.domain.usecase.UpdateSettingsUseCase
import com.hopcape.odo.feature.profile.domain.usecase.account
import com.hopcape.odo.feature.profile.domain.usecase.entitlement
import com.hopcape.odo.feature.profile.domain.usecase.session
import com.hopcape.odo.feature.profile.domain.usecase.testProfile
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceEvent
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsEvent
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsViewModel
import com.hopcape.odo.feature.profile.presentation.state.Loadable
import com.hopcape.odo.feature.profile.presentation.state.Submission
import com.hopcape.odo.feature.profile.presentation.state.valueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileViewModelsTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun observeProfile(
        profiles: FakeProfileRepository = FakeProfileRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        isPro: Boolean = false,
        isSignedIn: Boolean = false,
        phoneNumber: PhoneNumber? = null,
    ) = ObserveProfileUseCase(
        profiles = profiles,
        settings = settings,
        entitlement = entitlement(isPro),
        session = session(isSignedIn),
        account = account(phoneNumber),
    )

    /* ---------------------------- home ---------------------------- */

    @Test
    fun home_showsTheOwnerTheirPlanAndTheirSettingsSummaries() = runTest {
        val settings = FakeSettingsRepository(
            AppSettings(
                theme = ThemePreference.DARK,
                distanceUnit = DistanceUnit.MILE,
                notifications = NotificationPreferences(customReminders = true),
            ),
        )
        val viewModel = ProfileViewModel(
            observeProfile = observeProfile(settings = settings, isPro = true),
            syncStatus = idleSync(),
            appInfo = FakeAppInfo("2.0.0", versionCode = 7L),
            telemetry = testTelemetry(),
        )

        val content = assertNotNull(viewModel.state.value.content.valueOrNull)
        assertEquals("Rahul", content.name)
        assertTrue(content.isPro)
        assertEquals(ThemePreference.DARK, content.theme)
        assertEquals(DistanceUnit.MILE, content.distanceUnit)
        // Four defaults plus the custom reminders this test switched on.
        assertEquals(5, content.notificationTopicsOn)
        assertEquals("2.0.0", viewModel.state.value.version)
        // The build number is a debug/stage detail, so what is expected depends on the
        // flavor these tests were compiled as rather than on anything the test set up.
        val expectedBuildNumber = if (BuildInfo.isRelease) null else 7L
        assertEquals(expectedBuildNumber, viewModel.state.value.buildNumber)
    }

    @Test
    fun home_reportsWhetherACityIsSet_becauseWithoutOneThereAreNoVerdicts() = runTest {
        val analytics = RecordingAnalytics()
        ProfileViewModel(
            observeProfile = observeProfile(),
            syncStatus = idleSync(),
            appInfo = FakeAppInfo(),
            telemetry = testTelemetry(analytics),
        )

        val opened = assertNotNull(analytics.last(ProfileTelemetry.Event.PROFILE_OPENED))
        assertEquals(false, opened[ProfileTelemetry.Key.HAS_CITY])
    }

    @Test
    fun home_reportsOpenedOncePerVisit_notOncePerEmission() = runTest {
        val analytics = RecordingAnalytics()
        val profiles = FakeProfileRepository()
        ProfileViewModel(
            observeProfile = observeProfile(profiles = profiles),
            syncStatus = idleSync(),
            appInfo = FakeAppInfo(),
            telemetry = testTelemetry(analytics),
        )

        profiles.stored.value = testProfile().withCity("Pune")

        assertEquals(
            1,
            analytics.events.count { it.first == ProfileTelemetry.Event.PROFILE_OPENED },
        )
    }

    /* ---------------------------- edit ---------------------------- */

    @Test
    fun edit_seedsTheFormFromTheStoredProfile() = runTest {
        val profiles = FakeProfileRepository(
            testProfile().withEmail(OwnerEmail.of("rahul@example.com").getOrNull()).withCity("Pune"),
        )

        val viewModel = editViewModel(profiles)

        val state = viewModel.state.value
        assertEquals("Rahul", state.name.value)
        assertEquals("rahul@example.com", state.email.value)
        assertEquals("Pune", state.city.value)
        assertTrue(state.cities.contains("Pune"))
    }

    @Test
    fun edit_showsTheVerifiedNumber_whichAuthOwnsAndTheProfileDoesNot() = runTest {
        val viewModel = editViewModel(phoneNumber = PhoneNumber.of("9812345678").getOrNull())

        assertEquals("+919812345678", viewModel.state.value.phoneNumber)
    }

    @Test
    fun edit_leavesTheNumberEmptyOnADeviceThatNeverSignedIn() = runTest {
        val viewModel = editViewModel(phoneNumber = null)

        assertNull(viewModel.state.value.phoneNumber)
    }

    @Test
    fun edit_savesAndReportsThatTheCityMoved() = runTest {
        val analytics = RecordingAnalytics()
        val profiles = FakeProfileRepository()
        val viewModel = editViewModel(profiles, analytics)

        viewModel.onEvent(EditProfileEvent.CityChanged("Pune"))
        viewModel.onEvent(EditProfileEvent.Save)

        assertEquals(EditProfileEffect.Saved, viewModel.effects.first())
        assertEquals("Pune", profiles.stored.value?.city)
        val saved = assertNotNull(analytics.last(ProfileTelemetry.Event.DETAILS_SAVED))
        assertEquals(true, saved[ProfileTelemetry.Key.CITY_CHANGED])
    }

    @Test
    fun edit_putsEachFailureOnItsOwnField() = runTest {
        val viewModel = editViewModel()

        viewModel.onEvent(EditProfileEvent.NameChanged("R"))
        viewModel.onEvent(EditProfileEvent.EmailChanged("nope"))
        viewModel.onEvent(EditProfileEvent.Save)

        val state = viewModel.state.value
        assertNotNull(state.name.error)
        assertNotNull(state.email.error)
        assertEquals(Submission.Idle, state.submission)
    }

    @Test
    fun edit_deleteAsksFirst_thenWipesAndLeaves() = runTest {
        val profiles = FakeProfileRepository()
        val cars = FakeCarRepository()
        val viewModel = editViewModel(profiles, cars = cars)

        viewModel.onEvent(EditProfileEvent.DeleteRequested)
        assertTrue(viewModel.state.value.confirmingDelete)

        viewModel.onEvent(EditProfileEvent.DeleteConfirmed)

        assertEquals(EditProfileEffect.DataDeleted, viewModel.effects.first())
        assertNull(profiles.stored.value)
        assertEquals(1, cars.softDeleted.size)
    }

    @Test
    fun edit_deleteCanBeCalledOff() = runTest {
        val profiles = FakeProfileRepository()
        val viewModel = editViewModel(profiles)

        viewModel.onEvent(EditProfileEvent.DeleteRequested)
        viewModel.onEvent(EditProfileEvent.DeleteDismissed)

        assertTrue(!viewModel.state.value.confirmingDelete)
        assertNotNull(profiles.stored.value)
    }

    @Test
    fun edit_pickingAPhotoStoresItAndShowsIt() = runTest {
        val profiles = FakeProfileRepository()
        val viewModel = editViewModel(profiles)

        viewModel.onEvent(EditProfileEvent.PhotoPicked("content://picked/photo"))

        assertEquals("avatars/owner-1.jpg", viewModel.state.value.avatarPath)
        assertEquals("avatars/owner-1.jpg", profiles.stored.value?.avatarPath)
    }

    /* ---------------------------- settings ---------------------------- */

    @Test
    fun notifications_aToggleIsStoredImmediately() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = NotificationsViewModel(
            settings = settings,
            updateSettings = UpdateSettingsUseCase(settings),
            telemetry = testTelemetry(),
        )

        viewModel.onEvent(NotificationsEvent.HealthScoreDropsToggled(true))

        assertTrue(settings.stored.value.notifications.healthScoreDrops)
        assertTrue(viewModel.state.value.preferences.healthScoreDrops)
    }

    @Test
    fun notifications_aFailedWriteLeavesTheSwitchWhereItWas() = runTest {
        val settings = FakeSettingsRepository(failing = true)
        val viewModel = NotificationsViewModel(
            settings = settings,
            updateSettings = UpdateSettingsUseCase(settings),
            telemetry = testTelemetry(),
        )

        viewModel.onEvent(NotificationsEvent.DocumentExpiryToggled(false))

        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.preferences.documentExpiry, "the stored value still stands")
    }

    @Test
    fun appearance_storesTheThemeAsItIsTapped() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = AppearanceViewModel(
            settings = settings,
            updateSettings = UpdateSettingsUseCase(settings),
            telemetry = testTelemetry(),
        )

        viewModel.onEvent(AppearanceEvent.ThemeChosen(ThemePreference.LIGHT))
        viewModel.onEvent(AppearanceEvent.LargerTextToggled(true))

        assertEquals(ThemePreference.LIGHT, settings.stored.value.theme)
        assertTrue(settings.stored.value.largerText)
    }

    @Test
    fun units_storesEachChoiceWithoutDisturbingTheOther() = runTest {
        val settings = FakeSettingsRepository()
        val analytics = RecordingAnalytics()
        val viewModel = UnitsViewModel(
            settings = settings,
            updateSettings = UpdateSettingsUseCase(settings),
            telemetry = testTelemetry(analytics),
        )

        viewModel.onEvent(UnitsEvent.DistanceUnitChosen(DistanceUnit.MILE))
        viewModel.onEvent(UnitsEvent.FuelEfficiencyUnitChosen(FuelEfficiencyUnit.UNITS_PER_100KM))

        assertEquals(DistanceUnit.MILE, settings.stored.value.distanceUnit)
        assertEquals(FuelEfficiencyUnit.UNITS_PER_100KM, settings.stored.value.fuelEfficiencyUnit)
        val changed = assertNotNull(analytics.last(ProfileTelemetry.Event.SETTING_CHANGED))
        assertEquals(ProfileTelemetry.Setting.FUEL_EFFICIENCY_UNIT, changed[ProfileTelemetry.Key.SETTING])
    }

    private fun editViewModel(
        profiles: FakeProfileRepository = FakeProfileRepository(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
        cars: FakeCarRepository = FakeCarRepository(),
        phoneNumber: PhoneNumber? = null,
    ): EditProfileViewModel {
        val settings = FakeSettingsRepository()
        val files = FakeFileStore()
        return EditProfileViewModel(
            observeProfile = ObserveProfileUseCase(
                profiles = profiles,
                settings = settings,
                entitlement = entitlement(false),
                session = session(false),
                account = account(phoneNumber),
            ),
            updateDetails = UpdateOwnerDetailsUseCase(profiles),
            setAvatar = SetAvatarUseCase(profiles, files),
            deleteAllData = DeleteAllDataUseCase(cars, profiles, settings, files),
            telemetry = testTelemetry(analytics),
        )
    }

    /**
     * A device with nothing waiting and no run in flight — the profile tests are about the
     * profile, and a sync provider that emitted would only add noise.
     */
    private fun idleSync() = object : SyncStatusProvider {
        override val isSyncing = flowOf(false)
        override val pendingCount = flowOf(0)
        override val lastSyncedAt = flowOf<Instant?>(null)
        override val lastError = flowOf<String?>(null)
    }
}

/** Reads a [Loadable]'s value in tests without repeating the cast. */
private val <T> Loadable<T>.value: T? get() = valueOrNull
