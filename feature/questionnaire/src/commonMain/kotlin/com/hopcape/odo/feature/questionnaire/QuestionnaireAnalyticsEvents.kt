package com.hopcape.odo.feature.questionnaire

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.questionnaire.presentation.QuestionnaireTelemetry

/**
 * The questionnaire screen's analytics taxonomy — the surface reached from the profile, not
 * first-run setup, which declares its own list.
 *
 * Without this, strict-mode debug builds drop every event below. That is exactly what
 * happened: the screen shipped emitting four names nothing had registered.
 */
val questionnaireAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        QuestionnaireTelemetry.Event.OPENED,
        mapOf(QuestionnaireTelemetry.Key.QUESTIONS to PropertyType.STRING),
    ),
    EventSchema(
        QuestionnaireTelemetry.Event.ANSWERED,
        mapOf(
            QuestionnaireTelemetry.Key.QUESTION to PropertyType.STRING,
            QuestionnaireTelemetry.Key.COUNT to PropertyType.NUMBER,
        ),
    ),
    EventSchema(
        QuestionnaireTelemetry.Event.COMPLETED,
        mapOf(QuestionnaireTelemetry.Key.COUNT to PropertyType.NUMBER),
    ),
)
