package com.hopcape.odo.feature.billcheck

import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.billcheck.domain.BandBasisReader
import com.hopcape.odo.feature.billcheck.domain.BillCheckReader
import com.hopcape.odo.feature.billcheck.domain.LoggedBillCheckReader
import com.hopcape.odo.feature.billcheck.domain.UnavailableBandBasisReader
import com.hopcape.odo.feature.billcheck.domain.matching.BillLineMatcher
import com.hopcape.odo.feature.billcheck.domain.usecase.CheckBillPriceUseCase
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
 */
val billCheckModule = module {

    factory { BillLineMatcher() }
    factory {
        CheckBillPriceUseCase(matcher = get(), bands = get(), intervals = get())
    }

    // The bill is a service-log entry — that is where a scan lands, and it carries the lines
    // as the workshop printed them.
    single<BillCheckReader> {
        LoggedBillCheckReader(
            entries = get(),
            cars = get(),
            cities = get(),
            questionnaire = get(),
            check = get(),
            charger = get(),
            contributor = get(),
            ledger = get(),
            // A bill check spends the same balance a scan does — the packs grant scan checks
            // — so "may they see the answer" is the allowance's question, not a second one
            // that could drift from it.
            unlocked = { get<ScanAllowance>().current().allowsAnother },
        )
    }

    // Refuses, deliberately. Its own reader is the next slice — it needs the band the check
    // already resolved rather than a second lookup — and until then the sheet says it could
    // not read rather than showing a fixture's band beside a real finding.
    single<BandBasisReader> { UnavailableBandBasisReader() }

    // A factory, so one instance covers one visit to the screen.
    factory { BillCheckTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { (billId: String) ->
        BillCheckViewModel(billId = billId, reader = get(), telemetry = get())
    }

    viewModel { (billId: String, lineName: String) ->
        BasisViewModel(billId = billId, lineName = lineName, reader = get(), telemetry = get())
    }

    single { BillCheckFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
