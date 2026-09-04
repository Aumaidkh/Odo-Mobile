package com.hopcape.odo.feature.questionnaire.firstrun

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.CompleteOnboardingUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.RecordDeclaredServiceUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.ReportUnlistedVehicleUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.SaveCarUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.navigation.SetupFeatureEntryProvider
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingViewModel
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.SetupTelemetry
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for first-run setup — the car steps, the name and goals, and the first-scan prompt.
 *
 * Folded into `questionnaireModule` rather than listed separately in `initKoin`, so installing
 * the questionnaire installs everything it asks.
 */
internal val setupModule = module {

    factory { SaveCarUseCase(cars = get(), logs = get(), idGenerator = get(), clock = get()) }
    factory { LoadVehicleCatalogUseCase(catalog = get()) }
    factory { LoadCarModelsUseCase(catalog = get()) }
    factory { LookupPlateUseCase(registry = get()) }
    factory { ReportUnlistedVehicleUseCase(reporter = get()) }
    factory { RecordDeclaredServiceUseCase(logs = get(), idGenerator = get(), clock = get()) }
    factory { CompleteOnboardingUseCase(profiles = get(), currentOwner = get()) }

    // A factory, so one instance covers one attempt at setup.
    factory { SetupTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel {
        OnboardingViewModel(
            questions = get(),
            answers = get(),
            loadCatalog = get(),
            loadModels = get(),
            lookupPlate = get(),
            saveCar = get(),
            reportUnlisted = get(),
            recordDeclaredService = get(),
            completeOnboarding = get(),
            currentOwner = get(),
            // Published by :feature:auth via the shared :core:domain port — setup asks whether
            // to offer sign-in without knowing auth exists.
            sessionStatus = get(),
            telemetry = get(),
        )
    }

    single { SetupFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
