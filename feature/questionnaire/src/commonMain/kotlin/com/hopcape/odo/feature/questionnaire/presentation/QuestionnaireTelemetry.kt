package com.hopcape.odo.feature.questionnaire.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger

/**
 * All observability for the questionnaire, behind intent-named methods, so the ViewModel reads
 * as its own logic rather than as a wall of logger and analytics calls.
 *
 * Everything emitted is a constant this repo declares — a question key, an option name, a
 * count, an error type. Never anything the owner typed.
 */
class QuestionnaireTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
) {

    fun opened(keys: List<String>) =
        analytics.track(Event.OPENED, mapOf(Key.QUESTIONS to keys.joinToString(",")))

    fun answered(key: String, count: Int) =
        analytics.track(Event.ANSWERED, mapOf(Key.QUESTION to key, Key.COUNT to count))

    fun completed(count: Int) =
        analytics.track(Event.COMPLETED, mapOf(Key.COUNT to count))

    /** The error's type, never a message that could carry stored input. */
    fun saveFailed(key: String, error: Any) = logger.error(
        TAG,
        Event.SAVE_FAILED,
        fields = mapOf(Key.QUESTION to key, Key.ERROR to error::class.simpleName),
    )

    object Event {
        const val OPENED = "questionnaire_opened"
        const val ANSWERED = "questionnaire_answered"
        const val COMPLETED = "questionnaire_completed"
        const val SAVE_FAILED = "questionnaire_save_failed"
    }

    object Key {
        const val QUESTIONS = "questions"
        const val QUESTION = "question"
        const val COUNT = "count"
        const val ERROR = "error"
    }

    private companion object {
        const val TAG = "questionnaire"
    }
}
