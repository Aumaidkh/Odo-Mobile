/*
 * Copyright (c) 2026 Hopcape Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.hopcape.odo.feature.challan.di

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.challan.domain.usecase.LookupChallansUseCase
import com.hopcape.odo.feature.challan.domain.usecase.MarkChallansPaidUseCase
import com.hopcape.odo.feature.challan.domain.usecase.ObserveChallanOverviewUseCase
import com.hopcape.odo.feature.challan.domain.usecase.RefreshChallansUseCase
import com.hopcape.odo.feature.challan.navigation.ChallanFeatureEntryProvider
import com.hopcape.odo.feature.challan.presentation.ChallanTelemetry
import com.hopcape.odo.feature.challan.presentation.list.ChallanListViewModel
import com.hopcape.odo.feature.challan.presentation.lookup.ChallanLookupViewModel
import com.hopcape.odo.feature.challan.presentation.result.ChallanResultViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the challans feature. `ChallanRepository` comes from `coreDataModule`
 * (Fake source until Supabase overrides it), `ActiveCarProvider` + `CarRepository` from
 * the same, and the observability ports from the app bootstrap.
 */
val challanModule get() = module {
    single {
        ChallanFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    factory { ObserveChallanOverviewUseCase(challans = get()) }
    factory { RefreshChallansUseCase(challans = get()) }
    factory { MarkChallansPaidUseCase(challans = get()) }
    factory { LookupChallansUseCase(challans = get()) }

    // A `factory`, like the garage's: one instance covers one visit.
    factory { ChallanTelemetry(logger = get(), analytics = get()) }

    viewModel {
        ChallanListViewModel(
            activeCar = get(),
            cars = get(),
            observeOverview = get(),
            refresh = get(),
            markPaid = get(),
            telemetry = get(),
        )
    }
    viewModel { ChallanLookupViewModel(lookup = get(), telemetry = get()) }
    viewModel { (regNo: String) ->
        ChallanResultViewModel(regNoRaw = regNo, lookup = get())
    }
}
