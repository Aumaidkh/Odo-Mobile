package com.hopcape.odo.feature.onboarding

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoTelemetry

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
    // Declared late: it was named in `Event` but never here, so strict validation dropped it
    // in exactly the builds where the funnel gets checked.
    EventSchema(OnboardingTelemetry.Event.WELCOME_SIGN_IN),
    EventSchema(
        OnboardingTelemetry.Event.LEGAL_OPENED,
        mapOf(OnboardingTelemetry.Key.DOCUMENT to PropertyType.STRING),
    ),

    EventSchema(
        WelcomeVideoTelemetry.Event.SHOWN,
        mapOf(WelcomeVideoTelemetry.Key.PAGES to PropertyType.INT),
    ),
    EventSchema(
        WelcomeVideoTelemetry.Event.PAGE_VIEWED,
        mapOf(WelcomeVideoTelemetry.Key.PAGE to PropertyType.INT),
    ),
    EventSchema(
        WelcomeVideoTelemetry.Event.SKIPPED,
        mapOf(WelcomeVideoTelemetry.Key.PAGE to PropertyType.INT),
    ),
    EventSchema(WelcomeVideoTelemetry.Event.COMPLETED),
    EventSchema(
        WelcomeVideoTelemetry.Event.CLIP_FAILED,
        mapOf(WelcomeVideoTelemetry.Key.PAGE to PropertyType.INT),
    ),
)
