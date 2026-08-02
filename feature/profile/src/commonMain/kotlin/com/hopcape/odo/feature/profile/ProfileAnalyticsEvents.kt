package com.hopcape.odo.feature.profile

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.profile.presentation.ProfileTelemetry

/**
 * The profile's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Only properties the telemetry facade always sends are listed. It is the sole caller of
 * every name below, which is what makes that safe to promise.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val profileAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        ProfileTelemetry.Event.PROFILE_OPENED,
        mapOf(
            ProfileTelemetry.Key.IS_PRO to PropertyType.BOOLEAN,
            ProfileTelemetry.Key.SIGNED_IN to PropertyType.BOOLEAN,
            ProfileTelemetry.Key.HAS_CITY to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        ProfileTelemetry.Event.READ_FAILED,
        mapOf(
            ProfileTelemetry.Key.SOURCE to PropertyType.STRING,
            ProfileTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(
        ProfileTelemetry.Event.SETTINGS_OPENED,
        mapOf(ProfileTelemetry.Key.SOURCE to PropertyType.STRING),
    ),
    EventSchema(
        ProfileTelemetry.Event.DETAILS_SAVED,
        mapOf(ProfileTelemetry.Key.CITY_CHANGED to PropertyType.BOOLEAN),
    ),
    EventSchema(
        ProfileTelemetry.Event.DETAILS_SAVE_FAILED,
        mapOf(ProfileTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
    EventSchema(ProfileTelemetry.Event.AVATAR_SAVED),
    EventSchema(
        ProfileTelemetry.Event.AVATAR_FAILED,
        mapOf(ProfileTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
    EventSchema(
        ProfileTelemetry.Event.SETTING_CHANGED,
        mapOf(
            ProfileTelemetry.Key.SETTING to PropertyType.STRING,
            ProfileTelemetry.Key.VALUE to PropertyType.STRING,
        ),
    ),
    EventSchema(
        ProfileTelemetry.Event.SETTING_FAILED,
        mapOf(
            ProfileTelemetry.Key.SETTING to PropertyType.STRING,
            ProfileTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(
        ProfileTelemetry.Event.EXPORT_REQUESTED,
        mapOf(ProfileTelemetry.Key.TARGET to PropertyType.STRING),
    ),
    EventSchema(ProfileTelemetry.Event.SIGN_IN_STARTED),
    EventSchema(ProfileTelemetry.Event.SIGNED_OUT),
    EventSchema(ProfileTelemetry.Event.DATA_DELETED),
    EventSchema(
        ProfileTelemetry.Event.DELETE_FAILED,
        mapOf(ProfileTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
)
