package com.hopcape.odo.feature.refuel

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.FuelFillDraftInput
import com.hopcape.odo.feature.refuel.domain.RefuelDetectionWorker
import com.hopcape.odo.feature.refuel.domain.detectionCopy
import com.hopcape.odo.feature.refuel.domain.usecase.BuildFillDraftUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.CountDetectedFillsUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.DetectFillFromNoticeUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.GetTankInsightUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.IgnoreMerchantUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.LogRefuelUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.ResolvePendingFillUseCase
import com.hopcape.odo.feature.refuel.navigation.RefuelFeatureEntryProvider
import com.hopcape.odo.feature.refuel.presentation.RefuelTelemetry
import com.hopcape.odo.feature.refuel.presentation.autodetect.AutoDetectViewModel
import com.hopcape.odo.feature.refuel.presentation.confirm.RefuelConfirmViewModel
import com.hopcape.odo.feature.refuel.presentation.log.RefuelLogViewModel
import com.hopcape.odo.feature.refuel.presentation.pending.PendingFillsViewModel
import com.hopcape.odo.feature.refuel.presentation.logged.RefuelLoggedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the refuel feature.
 *
 * The repositories, the fuel-price and odometer ports, `Clock` and `IdGenerator` come from
 * `coreDataModule` and `coreCommonModule`; `NotificationAccess` from the platform module;
 * `RefuelDetectionStore` from the database infrastructure module. The
 * [RefuelFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks it up
 * via `getAll<FeatureEntryProvider>()`.
 *
 * Two ViewModels take a navigation argument, so they are declared with an explicit
 * `viewModel { }` rather than `viewModelOf`: the draft and the fill id arrive on the key,
 * and the route passes them through `parametersOf`.
 */
val refuelModule = module {
    single {
        RefuelFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    factory {
        BuildFillDraftUseCase(
            cars = get(),
            fills = get(),
            logs = get(),
            fuelPrices = get(),
            city = get(),
            clock = get(),
        )
    }
    factory { LogRefuelUseCase(fills = get(), owner = get(), ids = get(), clock = get()) }
    factory { GetTankInsightUseCase(fills = get()) }
    factory { IgnoreMerchantUseCase(store = get()) }
    factory { CountDetectedFillsUseCase(fills = get()) }
    factory { ResolvePendingFillUseCase(pending = get(), notifier = get(), clock = get()) }
    factory {
        DetectFillFromNoticeUseCase(
            fills = get(),
            detection = get(),
            buildDraft = get(),
        )
    }

    // A `single`: it collects for the app's lifetime, and a second instance would double
    // every detected fill. Started once by the app bootstrap; while the flag is off its
    // `start` returns before subscribing to anything.
    single {
        RefuelDetectionWorker(
            notices = get(),
            detection = get(),
            detectFill = get(),
            logRefuel = get(),
            resolvePending = get(),
            notifier = get(),
            activeCar = get(),
            pending = get(),
            access = get(),
            allowance = get(),
            clock = get(),
            copy = detectionCopy(),
        )
    }

    // A `factory`, not a `single`: one instance covers one visit to a screen, and every
    // event of that visit shares one flow id.
    factory { RefuelTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { (draft: FuelFillDraftInput) ->
        RefuelConfirmViewModel(
            input = draft,
            activeCar = get(),
            fuelPrices = get(),
            buildDraft = get(),
            logRefuel = get(),
            ignoreMerchant = get(),
            resolvePendingFill = get(),
            telemetry = get(),
        )
    }
    viewModel { (fillId: String) ->
        RefuelLoggedViewModel(
            fillId = fillId,
            activeCar = get(),
            fills = get(),
            tankInsight = get(),
            telemetry = get(),
        )
    }
    viewModelOf(::RefuelLogViewModel)
    viewModel {
        AutoDetectViewModel(
            store = get(),
            access = get(),
            backgroundStart = get(),
            activeCar = get(),
            countDetected = get(),
            telemetry = get(),
        )
    }
    viewModel { PendingFillsViewModel(pending = get(), resolvePendingFill = get(), telemetry = get()) }
}
