package com.hopcape.odo.di

import com.hopcape.analytics.api.EventSchema
import com.hopcape.odo.core.data.appstatus.observability.appStatusAnalyticsEvents
import com.hopcape.odo.feature.autoodometer.autoOdometerAnalyticsEvents
import com.hopcape.odo.feature.onboarding.onboardingAnalyticsEvents
import com.hopcape.odo.feature.costtracker.costTrackerAnalyticsEvents
import com.hopcape.odo.feature.dashboard.dashboardAnalyticsEvents
import com.hopcape.odo.feature.documentvault.documentVaultAnalyticsEvents
import com.hopcape.odo.feature.fairnesscheck.fairnessCheckAnalyticsEvents
import com.hopcape.odo.feature.garage.garageAnalyticsEvents
import com.hopcape.odo.feature.healthscore.healthScoreAnalyticsEvents
import com.hopcape.odo.feature.profile.profileAnalyticsEvents
import com.hopcape.odo.feature.refuel.refuelAnalyticsEvents
import com.hopcape.odo.feature.reminders.remindersAnalyticsEvents
import com.hopcape.odo.feature.servicelog.serviceLogAnalyticsEvents
import com.hopcape.odo.core.sync.observability.syncAnalyticsEvents
import com.hopcape.odo.core.triptracker.observability.tripTrackerAnalyticsEvents
import com.hopcape.odo.feature.auth.authAnalyticsEvents
import com.hopcape.odo.feature.billscanner.billScannerAnalyticsEvents
import com.hopcape.odo.feature.timeline.timelineAnalyticsEvents

/**
 * The app's analytics taxonomy — every feature's declared events, assembled in one place
 * for the platform bootstrap to hand to `AnalyticsConfig.events`.
 *
 * The same shape as [initKoin], and for the same reason: a feature contributes its own list
 * and the composition root concatenates them, so adding a feature's events is one line here
 * rather than a shared file every feature edits.
 *
 * Registering matters. Debug builds run strict schema validation and **drop** unregistered
 * events, so anything missing from this list is invisible in exactly the builds where the
 * funnel is being checked. A feature that ships telemetry ships its schema in the same change.
 */
val odoAnalyticsEvents: List<EventSchema> =
    onboardingAnalyticsEvents + serviceLogAnalyticsEvents + documentVaultAnalyticsEvents +
        garageAnalyticsEvents + costTrackerAnalyticsEvents + healthScoreAnalyticsEvents +
        timelineAnalyticsEvents + dashboardAnalyticsEvents + fairnessCheckAnalyticsEvents +
        profileAnalyticsEvents + syncAnalyticsEvents + authAnalyticsEvents +
        billScannerAnalyticsEvents + remindersAnalyticsEvents + tripTrackerAnalyticsEvents +
        autoOdometerAnalyticsEvents + appStatusAnalyticsEvents + refuelAnalyticsEvents
