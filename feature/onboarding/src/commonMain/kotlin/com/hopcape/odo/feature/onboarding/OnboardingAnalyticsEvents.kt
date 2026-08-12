package com.hopcape.odo.feature.onboarding

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry

/**
 * First-run setup's analytics taxonomy, declared for the tracker.
 *
 * Debug builds run strict schema validation and **drop** undeclared events, so until this
 * existed the entire acquisition funnel — welcome shown through completed setup — was
 * invisible in exactly the builds where it gets checked.
 *
 * Only properties the telemetry facade always sends are marked required; that facade is the
 * sole caller of every name below, which is what makes the promise safe to make.
 */
val onboardingAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(OnboardingTelemetry.Event.WELCOME_SHOWN),
    EventSchema(OnboardingTelemetry.Event.WELCOME_COMPLETED),
    EventSchema(
        OnboardingTelemetry.Event.LEGAL_OPENED,
        mapOf(OnboardingTelemetry.Key.DOCUMENT to PropertyType.STRING),
    ),

    EventSchema(OnboardingTelemetry.Event.STARTED),
    EventSchema(
        OnboardingTelemetry.Event.STEP_ADVANCED,
        mapOf(OnboardingTelemetry.Key.STEP to PropertyType.STRING),
    ),
    EventSchema(
        OnboardingTelemetry.Event.ABANDONED,
        mapOf(OnboardingTelemetry.Key.STEP to PropertyType.STRING),
    ),
    EventSchema(
        OnboardingTelemetry.Event.MANUAL_ENTRY_CHOSEN,
        mapOf(OnboardingTelemetry.Key.HAD_MATCH to PropertyType.BOOLEAN),
    ),
    EventSchema(
        OnboardingTelemetry.Event.PLATE_LOOKUP,
        mapOf(OnboardingTelemetry.Key.OUTCOME to PropertyType.STRING),
    ),
    EventSchema(
        OnboardingTelemetry.Event.GOAL_SELECTED,
        mapOf(OnboardingTelemetry.Key.GOAL to PropertyType.STRING),
    ),
    // Make and fuel are nullable at the call site (the plate route may have neither), so they
    // are declared by absence rather than as required properties — a required property with a
    // null value is exactly what strict validation throws on.
    EventSchema(
        OnboardingTelemetry.Event.CAR_SAVED,
        mapOf(OnboardingTelemetry.Key.EDIT to PropertyType.BOOLEAN),
    ),
    EventSchema(OnboardingTelemetry.Event.PROFILE_SAVED),
    EventSchema(
        OnboardingTelemetry.Event.SAVE_FAILED,
        mapOf(
            OnboardingTelemetry.Key.STEP to PropertyType.STRING,
            OnboardingTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(OnboardingTelemetry.Event.FIRST_SCAN_CLICKED),
    EventSchema(OnboardingTelemetry.Event.FIRST_SCAN_SKIPPED),
    EventSchema(
        OnboardingTelemetry.Event.COMPLETED,
        mapOf(
            OnboardingTelemetry.Key.GOAL to PropertyType.STRING,
            OnboardingTelemetry.Key.DESTINATION to PropertyType.STRING,
            OnboardingTelemetry.Key.SIGN_IN_OFFERED to PropertyType.BOOLEAN,
        ),
    ),
)
