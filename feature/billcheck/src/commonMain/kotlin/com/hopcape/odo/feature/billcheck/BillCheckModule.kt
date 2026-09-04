package com.hopcape.odo.feature.billcheck

import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.billcheck.domain.BandBasisReader
import com.hopcape.odo.feature.billcheck.domain.BillCheckReader
import com.hopcape.odo.feature.billcheck.domain.StubBillCheckReader
import com.hopcape.odo.feature.billcheck.navigation.BillCheckFeatureEntryProvider
import com.hopcape.odo.feature.billcheck.presentation.BillCheckTelemetry
import com.hopcape.odo.feature.billcheck.presentation.howweknow.BasisViewModel
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the bill check.
 *
 * The only public declaration in the module, per the minimal-surface rule: everything it
 * builds is `internal`, and Koin resolves by type regardless.
 *
 * Both readers are the stub today. Replacing them is these two lines — nothing above the
 * ports knows which one it got.
 */
val billCheckModule = module {

    single { StubBillCheckReader() }
    single<BillCheckReader> { get<StubBillCheckReader>() }
    single<BandBasisReader> { get<StubBillCheckReader>() }

    // A factory, so one instance covers one visit to the screen.
    factory { BillCheckTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { (billId: String) ->
        BillCheckViewModel(
            billId = billId,
            reader = get(),
            // A bill check spends the same balance a scan does — the packs grant scan
            // checks — so "can they see the answer" is the allowance's question, not a
            // second one that could drift from it.
            unlocked = { get<ScanAllowance>().current().allowsAnother },
            telemetry = get(),
        )
    }

    viewModel { (billId: String, lineName: String) ->
        BasisViewModel(billId = billId, lineName = lineName, reader = get(), telemetry = get())
    }

    single { BillCheckFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
