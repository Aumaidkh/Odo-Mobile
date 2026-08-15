package com.hopcape.odo.feature.servicelog

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.servicelog.domain.usecase.AddServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.AttachBillPhotoUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.DeleteServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetCurrentOdometerUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveEntryDetailUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceLogFeedUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceRecordUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.RecordEntryFairnessUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ReportOverchargeUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ResolveEntryFairnessUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.UpdateServiceLogUseCase
import com.hopcape.odo.feature.servicelog.navigation.ServiceLogFeatureEntryProvider
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.detail.ServiceLogDetailViewModel
import com.hopcape.odo.feature.servicelog.presentation.form.ServiceLogFormViewModel
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogListViewModel
import com.hopcape.odo.feature.servicelog.presentation.report.ReportOverchargeViewModel
import com.hopcape.odo.feature.servicelog.presentation.share.ShareRecordViewModel
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.BrandedServiceBillDocumentFactory
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.BrandedServiceRecordDocumentFactory
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.ServiceBillDocumentFactory
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.ServiceRecordDocumentFactory
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the service-log feature. `ServiceLogRepository`, `FairnessRepository`,
 * `OverchargeReportRepository`, `CarRepository` and `CurrentCityProvider` come from
 * `coreDataModule`, `PlatformFileStore` from the bootstrap's platform module,
 * `NavigationManager` from `coreNavigationModule`, and the three
 * observability ports from the `:observability:*` modules; the `:app` host registers
 * them all.
 *
 * The [ServiceLogFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks
 * it up via `getAll<FeatureEntryProvider>()` and adds servicelog to the graph.
 *
 * **One ViewModel per destination**, each taking the ids its route was opened with as Koin
 * parameters — `parametersOf(CarId(key.carId))` and friends. They are passed as the domain
 * value objects rather than raw strings so the definitions can look them up by type: an id
 * that is a `String` is indistinguishable from any other `String` in the parameter list.
 */
val serviceLogModule = module {

    factory { ObserveServiceLogFeedUseCase(logs = get()) }
    factory { ObserveEntryDetailUseCase(observeFeed = get()) }
    // The five ports the printed record is assembled from. The clock dates the document,
    // so a sheet left open across midnight still says the day it was produced.
    factory {
        ObserveServiceRecordUseCase(
            cars = get(),
            logs = get(),
            documents = get(),
            scores = get(),
            owners = get(),
            clock = get(),
        )
    }
    factory { GetServiceLogUseCase(logs = get()) }
    factory { GetCurrentOdometerUseCase(currentOdometer = get()) }
    factory { AddServiceLogUseCase(logs = get(), idGenerator = get(), clock = get()) }
    factory { UpdateServiceLogUseCase(logs = get(), clock = get()) }
    factory { DeleteServiceLogUseCase(logs = get()) }
    factory { ReportOverchargeUseCase(reports = get()) }
    // The bill-to-verdict pair. Attaching copies the picked file through :core:platform's
    // store; recording asks the shared benchmark pool and freezes what it said.
    factory { AttachBillPhotoUseCase(logs = get(), files = get()) }
    factory { ResolveEntryFairnessUseCase(fairness = get()) }
    factory {
        RecordEntryFairnessUseCase(
            logs = get(),
            resolveFairness = get(),
            currentCity = get(),
            clock = get(),
        )
    }

    // A `factory`, not a `single`: each instance mints its own trace id, so one instance
    // covers one visit to the service log. Every screen of the feature shares the flow id
    // regardless, which is what stitches list → entry → report into one journey.
    // Reads the feature's copy and the brand font, so it is bound behind its interface and
    // the share ViewModel's own rules stay testable without an Android runtime.
    factory<ServiceRecordDocumentFactory> { BrandedServiceRecordDocumentFactory() }
    factory<ServiceBillDocumentFactory> { BrandedServiceBillDocumentFactory() }

    factory { ServiceLogTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { params ->
        ServiceLogListViewModel(
            carId = params.get<CarId>(),
            observeFeed = get(),
            telemetry = get(),
        )
    }

    viewModel { params ->
        ServiceLogDetailViewModel(
            carId = params.get<CarId>(),
            logId = params.get<ServiceLogId>(),
            observeDetail = get(),
            deleteLog = get(),
            attachBillPhoto = get(),
            recordFairness = get(),
            telemetry = get(),
        )
    }

    viewModel { params ->
        ServiceLogFormViewModel(
            carId = params.get<CarId>(),
            // Absent for an add; `getOrNull` is what makes one definition serve both modes.
            editLogId = params.getOrNull<ServiceLogId>(),
            addLog = get(),
            updateLog = get(),
            getLog = get(),
            currentOdometer = get(),
            currentOwner = get(),
            settings = get(),
            telemetry = get(),
        )
    }

    viewModel { params ->
        ReportOverchargeViewModel(
            logId = params.get<ServiceLogId>(),
            getLog = get(),
            submitReport = get(),
            telemetry = get(),
        )
    }

    viewModel { params ->
        ShareRecordViewModel(
            carId = params.get<CarId>(),
            // Absent for a whole-record share; present when one entry's bill is going out.
            logId = params.getOrNull<ServiceLogId>(),
            observeRecord = get(),
            observeDetail = get(),
            entitlements = get(),
            documents = get(),
            bills = get(),
            files = get(),
            telemetry = get(),
        )
    }

    single {
        ServiceLogFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
