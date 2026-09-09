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

package com.hopcape.odo.feature.challan.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.challan.presentation.list.ChallanListEffect
import com.hopcape.odo.feature.challan.presentation.list.ChallanListScreen
import com.hopcape.odo.feature.challan.presentation.list.ChallanListViewModel
import com.hopcape.odo.feature.challan.presentation.lookup.ChallanLookupEffect
import com.hopcape.odo.feature.challan.presentation.lookup.ChallanLookupScreen
import com.hopcape.odo.feature.challan.presentation.lookup.ChallanLookupViewModel
import com.hopcape.odo.feature.challan.presentation.result.ChallanResultEffect
import com.hopcape.odo.feature.challan.presentation.result.ChallanResultScreen
import com.hopcape.odo.feature.challan.presentation.result.ChallanResultViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Challans' contribution to the navigation graph — the [OdoDestination.Challan] group:
 * the owner's list (reached from the garage), the buyer's lookup, and the lookup result.
 *
 * Payment never happens here: every pay affordance opens the official Parivahan site in
 * the browser, which is why the routes hand the URL to the platform's [LocalUriHandler]
 * rather than to any in-app surface.
 */
internal class ChallanFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {

    private val nm get() = navigationManager

    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Challan.List> { ChallanListRoute(nm) }
        entry<OdoDestination.Challan.Lookup> { ChallanLookupRoute(nm) }
        entry<OdoDestination.Challan.Result> { key -> ChallanResultRoute(nm, key.regNo) }
    }
}

@Composable
private fun ChallanListRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<ChallanListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ChallanListEffect.NavigateBack -> navigationManager.back()
            is ChallanListEffect.OpenParivahan -> uriHandler.openUri(effect.url)
        }
    }

    ChallanListScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun ChallanLookupRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<ChallanLookupViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ChallanLookupEffect.NavigateBack -> navigationManager.back()
            is ChallanLookupEffect.OpenResult ->
                navigationManager.navigateTo(OdoDestination.Challan.Result(regNo = effect.regNo))
        }
    }

    ChallanLookupScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun ChallanResultRoute(navigationManager: NavigationManager, regNo: String) {
    val viewModel = koinViewModel<ChallanResultViewModel> { parametersOf(regNo) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ChallanResultEffect.NavigateBack -> navigationManager.back()
        }
    }

    ChallanResultScreen(state = state, onEvent = viewModel::onEvent)
}
