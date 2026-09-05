package com.hopcape.odo.feature.support

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.support.domain.RequestDiagnosticsUseCase
import com.hopcape.odo.feature.support.domain.ReplyAddress
import com.hopcape.odo.feature.support.domain.usecase.CastIdeaVoteUseCase
import com.hopcape.odo.feature.support.domain.usecase.SubmitTicketUseCase
import com.hopcape.odo.feature.support.navigation.SupportFeatureEntryProvider
import com.hopcape.odo.feature.support.presentation.SupportTelemetry
import com.hopcape.odo.feature.support.presentation.flagprice.DisputedBand
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceViewModel
import com.hopcape.odo.feature.support.presentation.idea.SuggestIdeaViewModel
import com.hopcape.odo.feature.support.presentation.report.ReportProblemViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the support feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * [SupportFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks it up
 * via `getAll<FeatureEntryProvider>()` and the support destinations resolve.
 */
val supportModule = module {
    // Opening a diagnostics request: the reference code, the outbox row and the upload nudge.
    // A use case rather than a call in the entry provider, because the code has to be written
    // down before it is shown and that is a decision, not navigation.
    factory {
        RequestDiagnosticsUseCase(
            // Bound by corePlatform{Android,Ios}Module.
            installationId = get(),
            // The outbox, bound by databaseInfrastructureModule.
            requests = get(),
            scheduler = get(),
            clock = get(),
        )
    }

    // The three forms. One use case behind all of them: what a submission does is identical
    // the moment its fields are collected.
    factory {
        SubmitTicketUseCase(
            tickets = get(),
            // Attachments are copied into app storage before the row is written — a picker
            // reference stops resolving after a restart.
            files = get(),
            ids = get(),
            clock = get(),
        )
    }
    factory { CastIdeaVoteUseCase(ideas = get()) }
    factory { ReplyAddress(profiles = get()) }
    factory { SupportTelemetry(logger = get(), analytics = get(), tracer = get()) }

    viewModel {
        ReportProblemViewModel(
            submit = get(),
            replyAddress = get(),
            // The use case's one answer, not the class: what the view model needs is a
            // reference, and naming the type here would make every test build four fakes.
            requestDiagnostics = { get<RequestDiagnosticsUseCase>().invoke() },
            telemetry = get(),
        )
    }
    viewModel {
        SuggestIdeaViewModel(
            ideas = get(),
            castVote = get(),
            submit = get(),
            telemetry = get(),
        )
    }
    viewModel { (band: DisputedBand?) ->
        FlagPriceViewModel(band = band, submit = get(), telemetry = get())
    }

    single {
        SupportFeatureEntryProvider(
            navigationManager = get(),
            requestDiagnostics = get(),
            // AppInfo comes from corePlatformModule — the version shown on the help sheet.
            appInfo = get(),
            // The phone the report is about — model and OS only, shown in the draft before
            // the owner sends it. Bound by corePlatform{Android,Ios}Module alongside AppInfo.
            deviceInfo = get(),
            // Where the full Terms and Privacy Policy are published. Bound by
            // `supabaseModule`, which derives them from the configured project URL; a build
            // with no backend gets blanks and the outbound rows are left out.
            legalLinks = get(),
            // Where support mail goes. Configured in the Firebase console and falling back
            // to the address compiled into the build, so it is never blank.
            supportContacts = get(),
        )
    } bind FeatureEntryProvider::class
}
