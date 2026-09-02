package com.hopcape.odo.feature.questionnaire

import org.koin.dsl.module

/**
 * DI graph for the questionnaire.
 *
 * Only the registry for now. The nav entry and view model land with the screen in the next
 * slice; `QuestionnaireRepository` is already published by `coreDataModule`.
 */
val questionnaireModule = module {
    single { odoQuestions() }
}
