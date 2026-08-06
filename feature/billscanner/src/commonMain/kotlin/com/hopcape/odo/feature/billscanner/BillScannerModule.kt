package com.hopcape.odo.feature.billscanner

import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.feature.billscanner.domain.usecase.LogFuelFillUseCase
import com.hopcape.odo.feature.billscanner.domain.usecase.SaveScannedBillUseCase
import com.hopcape.odo.feature.billscanner.domain.usecase.SaveScannedDocumentUseCase
import com.hopcape.odo.feature.billscanner.domain.usecase.ScanBillUseCase
import com.hopcape.odo.feature.billscanner.domain.usecase.ScanDocumentUseCase
import com.hopcape.odo.feature.billscanner.navigation.BillScannerFeatureEntryProvider
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTelemetry
import com.hopcape.odo.feature.billscanner.presentation.document.DocumentReviewViewModel
import com.hopcape.odo.feature.billscanner.presentation.pay.PayAtPumpViewModel
import com.hopcape.odo.feature.billscanner.presentation.review.BillReviewViewModel
import com.hopcape.odo.feature.billscanner.presentation.scan.BillScanViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the bill-scanner feature. `NavigationManager` comes from `coreNavigationModule`;
 * the `:app` host registers them all.
 *
 * The [BillScannerFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks it
 * up via `getAll<FeatureEntryProvider>()`.
 *
 * `BillExtractor`, `DocumentExtractor`, `ScanAllowance`, `ServiceLogRepository`,
 * `DocumentRepository`, `DocumentAllowance`, `FuelFillRepository` and `CarRepository` all come
 * from `coreDataModule`; `IdGenerator` + `Clock` from `coreCommonModule`. The extraction ports
 * have no implementation yet and refuse honestly — swapping in the Edge Function callers is a
 * change there, not here.
 */
val billScannerModule = module {
    single {
        BillScannerFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    factory { ScanBillUseCase(extractor = get(), allowance = get(), ids = get(), clock = get()) }
    factory { ScanDocumentUseCase(extractor = get(), allowance = get(), ids = get(), clock = get()) }
    factory { SaveScannedBillUseCase(logs = get(), ids = get(), clock = get()) }
    factory {
        SaveScannedDocumentUseCase(
            documents = get(),
            allowance = get(),
            ids = get(),
            clock = get(),
        )
    }
    factory { LogFuelFillUseCase(fills = get(), ids = get(), clock = get()) }

    // A `factory`, not a `single`: one instance covers one visit to the scanner, and every
    // screen of that visit shares its flow id.
    factory { BillScannerTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { params ->
        BillScanViewModel(
            initialTarget = params.getOrNull<ScanTarget>() ?: ScanTarget.Bill,
            allowance = get(),
            cropper = get(),
            // A picture from the gallery is copied into app storage and, in the payment
            // mode, read for a code — neither of which the camera path needs.
            files = get(),
            qrDecoder = get(),
            ids = get(),
            telemetry = get(),
        )
    }
    viewModel { params ->
        BillReviewViewModel(
            // Absent when the review was reached without a capture; the screen says so
            // rather than reading a photo that isn't there.
            photoKey = params.getOrNull<String>(),
            scanBill = get(),
            saveBill = get(),
            activeCar = get(),
            currentOwner = get(),
            telemetry = get(),
        )
    }
    viewModel { params ->
        DocumentReviewViewModel(
            photoKey = params.getOrNull<String>(),
            // What the caller already knew the paper was — the vault's RC row says RC.
            // Absent when nobody said, and then the read's own guess is used.
            initialType = params.getOrNull<DocumentType>(),
            scanDocument = get(),
            saveDocument = get(),
            activeCar = get(),
            currentOwner = get(),
            telemetry = get(),
        )
    }
    viewModel { params ->
        PayAtPumpViewModel(
            payload = params.get<String>(),
            logFill = get(),
            cars = get(),
            activeCar = get(),
            currentOwner = get(),
            telemetry = get(),
        )
    }
}
