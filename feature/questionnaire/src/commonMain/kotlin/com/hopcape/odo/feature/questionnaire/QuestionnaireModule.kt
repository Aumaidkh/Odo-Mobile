package com.hopcape.odo.feature.questionnaire

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.feature.questionnaire.navigation.QuestionnaireFeatureEntryProvider
import com.hopcape.odo.feature.questionnaire.presentation.QuestionnaireTelemetry
import com.hopcape.odo.feature.questionnaire.presentation.QuestionnaireViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the questionnaire. `QuestionnaireRepository` comes from `coreDataModule` and
 * `NavigationManager` from `coreNavigationModule`.
 *
 * The keys to ask are a route argument, so they arrive through `parametersOf` rather than
 * being bound here.
 */
val questionnaireModule = module {

    // Everything first-run setup asks: the car steps, the name and goals, the scan prompt.
    includes(com.hopcape.odo.feature.questionnaire.firstrun.setupModule)

    single { odoQuestions() }

    factory { QuestionnaireTelemetry(logger = get(), analytics = get()) }

    viewModel { params ->
        QuestionnaireViewModel(
            keys = params.get<List<QuestionKey>>(),
            registry = get(),
            repository = get(),
            telemetry = get(),
        )
    }

    single { QuestionnaireFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
