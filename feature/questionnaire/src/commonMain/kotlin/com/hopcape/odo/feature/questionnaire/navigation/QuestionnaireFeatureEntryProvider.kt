package com.hopcape.odo.feature.questionnaire.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.feature.questionnaire.presentation.QuestionnaireEffect
import com.hopcape.odo.feature.questionnaire.presentation.QuestionnaireScreen
import com.hopcape.odo.feature.questionnaire.presentation.QuestionnaireViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The questionnaire's contribution to the navigation graph. Collected by the host with
 * `getAll<FeatureEntryProvider>()`, so no other module references this one.
 */
internal class QuestionnaireFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Questionnaire> { key -> QuestionnaireRoute(key, navigationManager) }
    }
}

/**
 * A state-and-effects bridge and nothing more.
 *
 * Both finishing and going back pop this destination. It is asked from somewhere — onboarding,
 * the profile screen — and returns there; it never decides where to go next itself.
 */
@Composable
internal fun QuestionnaireRoute(
    key: OdoDestination.Questionnaire,
    navigationManager: NavigationManager,
) {
    val keys = key.keys.map(::QuestionKey)
    val viewModel = koinViewModel<QuestionnaireViewModel> { parametersOf(keys) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            QuestionnaireEffect.Finished -> navigationManager.back()
            QuestionnaireEffect.NavigateBack -> navigationManager.back()
            // TODO(ui): surface this in a snackbar. The screen scaffolds itself, so the host
            //  state has to reach it first. Until then a failed write shows as Continue not
            //  advancing.
            QuestionnaireEffect.SaveFailed -> Unit
        }
    }

    QuestionnaireScreen(state = state, onEvent = viewModel::onEvent)
}
