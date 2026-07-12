package com.hopcape.odo.feature.fairnesscheck

import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.fairnesscheck.navigation.FairnessCheckFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the reusable fairness-check feature. Binds the [FairnessAnalyzer] port to
 * its MVP [SampleFairnessAnalyzer] stand-in (swap for the RPC-backed adapter in M2 with
 * no caller change), and the entry provider that registers [OdoDestination.Fairness].
 */
val fairnessCheckModule = module {
    single<FairnessAnalyzer> { SampleFairnessAnalyzer() }
    single {
        FairnessCheckFeatureEntryProvider(navigationManager = get(), analyzer = get())
    } bind FeatureEntryProvider::class
}
