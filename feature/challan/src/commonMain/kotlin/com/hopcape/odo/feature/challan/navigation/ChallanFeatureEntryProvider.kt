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

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination

class ChallanFeatureEntryProvider(
    private val navigationManager: NavigationManager,
): FeatureEntryProvider {

    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Challan.List>{
            // TODO: Register List COmposablea
        }
        entry<OdoDestination.Challan.Lookup> {
            // TODO: Register List COmposable
        }
        entry<OdoDestination.Challan.Result> {
            // TODO: Register List COmposable
        }
    }

}