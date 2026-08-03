package com.hopcape.odo.feature.profile

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.profile.domain.usecase.DeleteAllDataUseCase
import com.hopcape.odo.feature.profile.domain.usecase.ObserveProfileUseCase
import com.hopcape.odo.feature.profile.domain.usecase.SetAvatarUseCase
import com.hopcape.odo.feature.profile.domain.usecase.UpdateOwnerDetailsUseCase
import com.hopcape.odo.feature.profile.domain.usecase.UpdateSettingsUseCase
import com.hopcape.odo.feature.profile.navigation.ProfileFeatureEntryProvider
import com.hopcape.odo.feature.profile.presentation.EditProfileViewModel
import com.hopcape.odo.feature.profile.presentation.NotificationsViewModel
import com.hopcape.odo.feature.profile.presentation.ProfileTelemetry
import com.hopcape.odo.feature.profile.presentation.ProfileViewModel
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
        )
    }
    factory { UpdateOwnerDetailsUseCase(profiles = get()) }
    factory { UpdateSettingsUseCase(settings = get()) }
    factory { SetAvatarUseCase(profiles = get(), files = get()) }
    factory {
        DeleteAllDataUseCase(cars = get(), profiles = get(), settings = get(), files = get())
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
    viewModel { AppearanceViewModel(settings = get(), updateSettings = get(), telemetry = get()) }
    viewModel { UnitsViewModel(settings = get(), updateSettings = get(), telemetry = get()) }
    // `SignOut` is bound by :feature:auth, which owns sessions. Profile only has the button.
    viewModel { SignOutViewModel(signOut = get(), telemetry = get()) }
}
