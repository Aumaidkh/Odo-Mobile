package com.hopcape.odo.feature.billcheck

import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.billcheck.domain.BandBasisReader
import com.hopcape.odo.feature.billcheck.domain.BillCheckReader
import com.hopcape.odo.feature.billcheck.domain.LoggedBillCheckReader
import com.hopcape.odo.feature.billcheck.domain.LoggedBandBasisReader
import com.hopcape.odo.feature.billcheck.domain.matching.BillLineMatcher
import com.hopcape.odo.feature.billcheck.domain.usecase.CheckBillPriceUseCase
import com.hopcape.odo.feature.billcheck.navigation.BillCheckFeatureEntryProvider
import com.hopcape.odo.feature.billcheck.presentation.BillCheckTelemetry
import com.hopcape.odo.feature.billcheck.presentation.howweknow.BasisViewModel
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckViewModel
import com.hopcape.odo.feature.billcheck.presentation.share.ShareCardViewModel
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
        CheckBillPriceUseCase(
            matcher = get(),
            bands = get(),
            intervals = get(),
            // The model, for lines the rules could not name. Bound to the offline classifier
            // that names nothing on a build with no credentials, and gated by a flag that
            // ships off — so this costs nothing until both are true.
            classifier = get(),
            config = get(),
        )
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

    single<BandBasisReader> {
        LoggedBandBasisReader(
            entries = get(),
            cars = get(),
            cities = get(),
            cityCatalog = get(),
            questionnaire = get(),
            matcher = get(),
            bands = get(),
        )
    }

    // A factory, so one instance covers one visit to the screen.
    factory { BillCheckTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { (billId: String) ->
        BillCheckViewModel(billId = billId, reader = get(), telemetry = get())
    }

    viewModel { (amountPaise: Long, flagged: Int, lines: Int) ->
        ShareCardViewModel(
            amountPaise = amountPaise,
            flagged = flagged,
            lines = lines,
            files = get(),
            downloads = get(),
            telemetry = get(),
        )
    }

    viewModel { (billId: String, lineName: String) ->
        BasisViewModel(billId = billId, lineName = lineName, reader = get(), telemetry = get())
    }

    single { BillCheckFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
