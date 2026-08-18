package com.hopcape.odo.feature.autoodometer.di

import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.DefaultPendingTripLoggedProvider
import com.hopcape.odo.feature.autoodometer.PendingTripLoggedProvider
import com.hopcape.odo.feature.autoodometer.domain.usecase.AcknowledgeTrip
import com.hopcape.odo.feature.autoodometer.domain.usecase.CompleteSetup
import com.hopcape.odo.feature.autoodometer.domain.usecase.DeleteAllTripData
import com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveDerivedOdometer
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveMonthlySummary
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObservePendingTripLogged
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveServiceDueNudge
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveSetupState
import com.hopcape.odo.feature.autoodometer.domain.usecase.PauseTracking
import com.hopcape.odo.feature.autoodometer.domain.usecase.RejectTrip
import com.hopcape.odo.feature.autoodometer.domain.usecase.ResumeTracking
import com.hopcape.odo.feature.autoodometer.navigation.AutoOdometerFeatureEntryProvider
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.presentation.devicepicker.DevicePickerViewModel
import com.hopcape.odo.feature.autoodometer.presentation.education.EducationViewModel
import com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupViewModel
import com.hopcape.odo.feature.autoodometer.presentation.settings.SettingsViewModel
import com.hopcape.odo.feature.autoodometer.presentation.triplogged.TripLoggedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the auto-odometer feature. `NavigationManager` comes from
 * `coreNavigationModule`; `TripTracker`, `VehicleBondStore` and `BondedDeviceCatalog` from
 * `coreTripTrackerModule`; the domain repository ports (`ServiceLogRepository`,
 * `TripRepository`, `AppSettingsRepository`) from `coreDataModule`; `Clock` from
 * `coreCommonModule`. `:app` (via `:shared`) registers them all before this module.
 *
 * F2 wired only the entry provider and the telemetry facade. F3 adds the feature's own
 * use cases (plan §4.1) — every one `internal`, resolved by type, never referenced by
 * `:app`. ViewModels are added here as F4-F8 build the real screens.
 */
val autoOdometerModule = module {
    single {
        AutoOdometerFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    // The one cross-feature contract this module exposes (plan §4.4) — `:shared`'s app
    // shell resolves this to know when to redirect onto the trip-logged screen.
    single<PendingTripLoggedProvider> {
        DefaultPendingTripLoggedProvider(observePendingTripLogged = get(), activeCar = get())
    }

    // A `factory`, not a `single`: one instance covers one visit to the feature, and every
    // screen of that visit shares one flow id (mirrors reminders/triptracker).
    factory {
        AutoOdometerTelemetry(
            logger = get(),
            analytics = get(),
            tracer = get(),
            crash = get(),
            ids = get(),
        )
    }

    factory { ObserveSetupState(serviceLogs = get(), bonds = get(), tracker = get(), preconditions = get()) }
    factory { EnrollTriggerDevice(bonds = get()) }
    factory { CompleteSetup(tracker = get(), settings = get()) }
    factory { ObservePendingTripLogged(trips = get(), settings = get()) }
    factory { AcknowledgeTrip(settings = get()) }
    factory { RejectTrip(trips = get()) }
    factory { ObserveDerivedOdometer(serviceLogs = get(), trips = get()) }
    factory { ObserveMonthlySummary(trips = get(), clock = get()) }
    factory { PauseTracking(settings = get(), tracker = get(), clock = get()) }
    factory { ResumeTracking(settings = get(), tracker = get()) }
    factory { DeleteAllTripData(trips = get()) }
    factory { ObserveServiceDueNudge(serviceLogs = get(), currentOdometer = get(), clock = get()) }

    // `mode` is a route argument (the route host maps the nav-local flow enum to
    // TriggerMode before calling in — see AutoOdometerEducationRoute).
    viewModel { (mode: TriggerMode) -> EducationViewModel(mode = mode, telemetry = get()) }

    // No route arguments: the picker resolves the active car itself, matching
    // UpdateOdometerViewModel's convention (see DevicePickerViewModel's KDoc).
    viewModel {
        DevicePickerViewModel(catalog = get(), enroll = get(), activeCar = get(), telemetry = get())
    }

    // `mode` is a route argument, mapped the same way EducationViewModel's is.
    viewModel { (mode: TriggerMode) ->
        PermissionSetupViewModel(
            mode = mode,
            enrollTriggerDevice = get(),
            completeSetup = get(),
            activeCar = get(),
            backgroundStart = get(),
            telemetry = get(),
        )
    }

    // `tripId` is a route argument (the redirect's TripId, mapped from the raw nav string at
    // AutoOdometerTripLoggedRoute).
    viewModel { (tripId: TripId) ->
        TripLoggedViewModel(
            tripId = tripId,
            trips = get(),
            observeDerivedOdometer = get(),
            observeServiceDueNudge = get(),
            acknowledgeTrip = get(),
            rejectTrip = get(),
            activeCar = get(),
            preconditions = get(),
            settings = get(),
            telemetry = get(),
        )
    }

    // No route arguments: resolves the active car itself, same convention as
    // DevicePickerViewModel/TripLoggedViewModel.
    viewModel {
        SettingsViewModel(
            tracker = get(),
            observeSetupState = get(),
            observeMonthlySummary = get(),
            pauseTracking = get(),
            resumeTracking = get(),
            deleteAllTripData = get(),
            settings = get(),
            activeCar = get(),
            clock = get(),
            telemetry = get(),
        )
    }
}
