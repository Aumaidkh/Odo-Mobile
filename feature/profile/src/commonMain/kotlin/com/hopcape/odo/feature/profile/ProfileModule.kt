package com.hopcape.odo.feature.profile

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.profile.domain.usecase.DeleteAccountUseCase
import com.hopcape.odo.feature.profile.domain.usecase.DeleteAllDataUseCase
import com.hopcape.odo.feature.profile.domain.usecase.ObserveProfileUseCase
import com.hopcape.odo.feature.profile.domain.usecase.SetAvatarUseCase
import com.hopcape.odo.feature.profile.domain.usecase.UpdateOwnerDetailsUseCase
import com.hopcape.odo.feature.profile.domain.usecase.UpdatePrivacyUseCase
import com.hopcape.odo.feature.profile.domain.usecase.UpdateSettingsUseCase
import com.hopcape.odo.feature.profile.navigation.ProfileFeatureEntryProvider
import com.hopcape.odo.feature.profile.presentation.EditProfileViewModel
import com.hopcape.odo.feature.profile.presentation.NotificationsViewModel
import com.hopcape.odo.feature.profile.presentation.ProfileTelemetry
import com.hopcape.odo.feature.profile.presentation.ProfileViewModel
import com.hopcape.odo.feature.profile.presentation.privacy.DeleteAccountViewModel
import com.hopcape.odo.feature.profile.presentation.privacy.PrivacyViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.SignOutViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the profile feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * [ProfileFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks it
 * up via `getAll<FeatureEntryProvider>()` and the profile destinations resolve.
 */
val profileModule = module {
    single {
        ProfileFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    factory {
        ObserveProfileUseCase(
            profiles = get(),
            settings = get(),
            entitlement = get(),
            session = get(),
            // Firebase's own user, same binding DeleteAccountUseCase resolves below.
            account = get(),
        )
    }
    factory { UpdateOwnerDetailsUseCase(profiles = get()) }
    factory {
        UpdateSettingsUseCase(
            settings = get(),
            documentReminders = get(),
            customReminders = get(),
        )
    }
    factory { SetAvatarUseCase(profiles = get(), files = get()) }
    factory {
        DeleteAllDataUseCase(cars = get(), profiles = get(), settings = get(), files = get())
    }
    factory { UpdatePrivacyUseCase(settings = get(), profiles = get(), trips = get()) }
    factory {
        DeleteAccountUseCase(
            // Bound by supabaseModule, and only on a build with credentials — see
            // DeleteAccountViewModel for what an unconfigured build does instead.
            eraser = get(),
            // Firebase's own user. The Android bootstrap binds the real one; iOS keeps the
            // unavailable stub, which answers NoVerifiedAccount.
            account = get(),
            deleteAllData = get(),
            verifier = get(),
        )
    }

    // A `factory`, not a `single`: one instance covers one visit to the profile, and every
    // screen and sheet of that visit shares its flow id.
    factory { ProfileTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel {
        ProfileViewModel(
            observeProfile = get(),
            appInfo = get(),
            syncStatus = get(),
            telemetry = get(),
        )
    }
    viewModel {
        EditProfileViewModel(
            observeProfile = get(),
            updateDetails = get(),
            setAvatar = get(),
            deleteAllData = get(),
            telemetry = get(),
        )
    }
    viewModel { NotificationsViewModel(settings = get(), updateSettings = get(), telemetry = get()) }
    viewModel {
        PrivacyViewModel(
            settings = get(),
            profiles = get(),
            updatePrivacy = get(),
            // The consent gate itself, not the telemetry facade: turning the switch off has
            // to stop tracking now, and the facade's job is to record that it happened.
            analytics = get(),
            telemetry = get(),
        )
    }
    viewModel { AppearanceViewModel(settings = get(), updateSettings = get(), telemetry = get()) }
    viewModel { UnitsViewModel(settings = get(), updateSettings = get(), telemetry = get()) }
    viewModel {
        DeleteAccountViewModel(
            account = get(),
            verifier = get(),
            deleteAccount = get(),
            telemetry = get(),
        )
    }
    // `SignOut` is bound by :feature:auth, which owns sessions. Profile only has the button.
    viewModel { SignOutViewModel(signOut = get(), telemetry = get()) }
}
