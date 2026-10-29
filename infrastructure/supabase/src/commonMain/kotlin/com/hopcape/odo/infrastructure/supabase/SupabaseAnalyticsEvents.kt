package com.hopcape.odo.infrastructure.supabase

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry.Companion.EVENT_OK
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry.Companion.EVENT_UNREACHABLE
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry.Key

private val requestFields = mapOf(
    Key.RESOURCE to PropertyType.STRING,
    Key.OPERATION to PropertyType.STRING,
    Key.FOREGROUND to PropertyType.BOOLEAN,
    Key.SESSION to PropertyType.STRING,
)

/** The request events, declared so debug builds keep them. Public for `odoAnalyticsEvents`. */
val supabaseAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(EVENT_OK, requestFields),
    EventSchema(EVENT_UNREACHABLE, requestFields + (Key.ERROR to PropertyType.STRING)),
)
