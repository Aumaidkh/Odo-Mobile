package com.hopcape.odo.feature.profile.domain.usecase

import com.hopcape.logging.api.DiagnosticRequests
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.owner.model.OwnerEmail
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.core.domain.subscription.SubscriptionHealth
import com.hopcape.odo.core.domain.subscription.SubscriptionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ProfileUseCasesTest {

    /* ---------------------------- observe ---------------------------- */

    @Test
    fun observe_readsTheOwnerPlanSessionAndSettingsTogether() = runTest {
        val profiles = FakeProfileRepository(
            testProfile().withEmail(OwnerEmail.of("rahul@example.com").getOrNull()).withCity("Pune"),
        )
        val settings = FakeSettingsRepository(AppSettings(theme = ThemePreference.DARK))

        val snapshot = ObserveProfileUseCase(
            profiles = profiles,
            settings = settings,
            subscription = subscription(),
            entitlements = entitlement(isPro = true),
            session = session(signedIn = false),
            account = account(PhoneNumber.of("9812345678").getOrNull()),
        )().first()

        assertEquals("Rahul", snapshot.name)
        assertEquals("rahul@example.com", snapshot.email)
        assertEquals("Pune", snapshot.city)
        assertEquals("+919812345678", snapshot.phoneNumber)
        assertTrue(snapshot.isPro)
        assertTrue(!snapshot.isSignedIn)
        assertEquals(ThemePreference.DARK, snapshot.settings.theme)
    }

    @Test
    fun observe_carriesTheLiveSubscriptionForThePlanCard() = runTest {
        // The renewal date and the billing-issue banner come from here. Nothing gates on it,
        // which is why it is a separate port from the entitlement.
        val state = SubscriptionState(
            period = BillingPeriod.ANNUAL,
            health = SubscriptionHealth.BILLING_ISSUE,
            renewsOn = LocalDate(2026, 9, 1),
            managementUrl = "https://play.google.com/store/account/subscriptions",
        )

        val snapshot = ObserveProfileUseCase(
            profiles = FakeProfileRepository(),
            settings = FakeSettingsRepository(),
            subscription = subscription(state),
            entitlements = entitlement(isPro = true),
            session = session(signedIn = true),
            account = account(),
        )().first()

        assertEquals(state, snapshot.subscription)
    }

    @Test
    fun observe_onTheFreePlanHasNoSubscriptionToDescribe() = runTest {
        val snapshot = ObserveProfileUseCase(
            profiles = FakeProfileRepository(),
            settings = FakeSettingsRepository(),
            subscription = subscription(),
            entitlements = entitlement(isPro = false),
            session = session(signedIn = false),
            account = account(),
        )().first()

        assertNull(snapshot.subscription, "there is nothing to say about a subscription that does not exist")
    }

    @Test
    fun observe_withNoProfileStored_stillReportsPlanAndSettings() = runTest {
        val snapshot = ObserveProfileUseCase(
            profiles = FakeProfileRepository(profile = null),
            settings = FakeSettingsRepository(),
            subscription = subscription(),
            entitlements = entitlement(isPro = false),
            session = session(signedIn = false),
            account = account(),
        )().first()

        assertNull(snapshot.name)
        assertNull(snapshot.phoneNumber)
        assertEquals(AppSettings.Default, snapshot.settings)
    }

    /* ---------------------------- details ---------------------------- */

    @Test
    fun updateDetails_savesNameEmailAndCity() = runTest {
        val profiles = FakeProfileRepository()

        val result = UpdateOwnerDetailsUseCase(profiles)(
            OwnerDetailsCommand(name = "Rahul Deshmukh", email = "rahul@example.com", city = "Pune"),
        )

        assertTrue(result.isRight(), "expected Right but was $result")
        val stored = profiles.stored.value
        assertEquals("Rahul Deshmukh", stored?.name?.value)
        assertEquals("rahul@example.com", stored?.email?.value)
        assertEquals("Pune", stored?.city)
    }

    @Test
    fun updateDetails_reportsEveryValidationFailureAtOnce() = runTest {
        val result = UpdateOwnerDetailsUseCase(FakeProfileRepository())(
            OwnerDetailsCommand(name = "R", email = "not-an-address"),
        )

        val errors = result.leftOrNull()
        assertEquals(
            listOf(DomainError.OwnerNameTooShort(2), DomainError.InvalidOwnerEmail),
            errors?.toList(),
        )
    }

    @Test
    fun updateDetails_blankEmailAndCity_clearThemRatherThanFailing() = runTest {
        val profiles = FakeProfileRepository(
            testProfile().withEmail(OwnerEmail.of("rahul@example.com").getOrNull()).withCity("Pune"),
        )

        val result = UpdateOwnerDetailsUseCase(profiles)(
            OwnerDetailsCommand(name = "Rahul", email = "  ", city = ""),
        )

        assertTrue(result.isRight(), "expected Right but was $result")
        assertNull(profiles.stored.value?.email)
        assertNull(profiles.stored.value?.city)
    }

    @Test
    fun updateDetails_withNoProfileStored_reportsProfileNotFound() = runTest {
        val result = UpdateOwnerDetailsUseCase(FakeProfileRepository(profile = null))(
            OwnerDetailsCommand(name = "Rahul"),
        )

        assertEquals(listOf(DomainError.ProfileNotFound), result.leftOrNull()?.toList())
    }

    /* ---------------------------- settings ---------------------------- */

    @Test
    fun updateSettings_eachSliceLeavesTheOthersAlone() = runTest {
        val settings = FakeSettingsRepository()
        val useCase = UpdateSettingsUseCase(settings, documentReminders = {}, customReminders = {})

        assertTrue(useCase.appearance(ThemePreference.LIGHT, largerText = true).isRight())
        assertTrue(useCase.units(DistanceUnit.MILE, FuelEfficiencyUnit.UNITS_PER_100KM).isRight())
        assertTrue(
            useCase.notifications(NotificationPreferences(documentExpiry = false)).isRight(),
        )

        val stored = settings.stored.value
        assertEquals(ThemePreference.LIGHT, stored.theme)
        assertTrue(stored.largerText)
        assertEquals(DistanceUnit.MILE, stored.distanceUnit)
        assertEquals(FuelEfficiencyUnit.UNITS_PER_100KM, stored.fuelEfficiencyUnit)
        assertTrue(!stored.notifications.documentExpiry)
    }

    @Test
    fun updateSettings_aFailedWriteIsReported() = runTest {
        val settings = FakeSettingsRepository(failing = true)

        val result = UpdateSettingsUseCase(settings, documentReminders = {}, customReminders = {}).appearance(ThemePreference.DARK, largerText = false)

        assertTrue(result.isLeft(), "expected Left but was $result")
    }

    /* ---------------------------- avatar ---------------------------- */

    @Test
    fun setAvatar_copiesThePhotoAndPointsTheProfileAtIt() = runTest {
        val profiles = FakeProfileRepository()
        val files = FakeFileStore()

        val result = SetAvatarUseCase(profiles, files)("content://picked/photo")

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("avatars/owner-1.jpg", profiles.stored.value?.avatarPath)
        assertEquals(listOf("avatars/owner-1.jpg"), files.saved)
    }

    @Test
    fun setAvatar_removesThePreviousPhotoOnlyAfterTheSaveLands() = runTest {
        val profiles = FakeProfileRepository(testProfile().withAvatar("avatars/old.jpg"))
        val files = FakeFileStore()

        assertTrue(SetAvatarUseCase(profiles, files)("content://picked/photo").isRight())

        assertEquals(listOf("avatars/old.jpg"), files.deleted)
    }

    @Test
    fun setAvatar_aFailedSave_leavesTheOldPhotoInPlace() = runTest {
        val profiles = FakeProfileRepository(testProfile().withAvatar("avatars/old.jpg"), failing = true)
        val files = FakeFileStore()

        assertTrue(SetAvatarUseCase(profiles, files)("content://picked/photo").isLeft())

        assertEquals(emptyList(), files.deleted)
        assertEquals("avatars/old.jpg", profiles.stored.value?.avatarPath)
    }

    /* ---------------------------- wipe ---------------------------- */

    @Test
    fun deleteAllData_removesTheCarTheProfileThePhotoAndTheSettings() = runTest {
        val cars = FakeCarRepository()
        val profiles = FakeProfileRepository(testProfile().withAvatar("avatars/owner-1.jpg"))
        val settings = FakeSettingsRepository(AppSettings(theme = ThemePreference.DARK))
        val files = FakeFileStore()

        val result = DeleteAllDataUseCase(cars, profiles, settings, files, FakeShowcaseSeenStore(), FakeDiagnosticRequests())()

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(listOf(testCar().id), cars.softDeleted)
        assertEquals(1, profiles.deleteCount)
        assertNull(profiles.stored.value)
        assertEquals(listOf("avatars/owner-1.jpg"), files.deleted)
        assertEquals(AppSettings.Default, settings.stored.value)
    }

    @Test
    fun deleteAllData_withNoCarSetUp_stillClearsTheProfile() = runTest {
        val cars = FakeCarRepository(car = null)
        val profiles = FakeProfileRepository()

        val result = DeleteAllDataUseCase(
            cars,
            profiles,
            FakeSettingsRepository(),
            FakeFileStore(),
            FakeShowcaseSeenStore(),
            FakeDiagnosticRequests(),
        )()

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(emptyList(), cars.softDeleted)
        assertEquals(1, profiles.deleteCount)
    }
    private class FakeDiagnosticRequests : DiagnosticRequests {
        var cleared = false
        override suspend fun open(reference: String, createdAtEpochMs: Long) = Unit
        override suspend fun oldestOpen(): String? = null
        override suspend fun markDelivered(reference: String) = Unit
        override suspend fun markAttemptFailed(reference: String, error: String?) = Unit
        override suspend fun clearAll() { cleared = true }
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
