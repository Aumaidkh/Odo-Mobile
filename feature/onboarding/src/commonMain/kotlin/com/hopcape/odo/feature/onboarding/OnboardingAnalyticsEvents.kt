package com.hopcape.odo.feature.onboarding

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry

/**
 * The first-run pitch's analytics taxonomy.
 *
 * Debug builds drop undeclared events, so a name missing here is invisible in exactly the
 * builds where it gets checked. The setup steps declare their own list in
 * `:feature:questionnaire`.
 */
val onboardingAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(OnboardingTelemetry.Event.WELCOME_SHOWN),
    EventSchema(OnboardingTelemetry.Event.WELCOME_COMPLETED),
    EventSchema(
        OnboardingTelemetry.Event.LEGAL_OPENED,
        mapOf(OnboardingTelemetry.Key.DOCUMENT to PropertyType.STRING),
    ),
)
