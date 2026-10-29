package com.hopcape.odo.infrastructure.supabase

import com.hopcape.analytics.api.problemsWith
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseServiceLogRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class SupabaseAnalyticsEventsTest {

    @Test
    fun everyRequestEventIsDeclared() = runTest {
        var offline = true
        val harness = SupabaseTestHarness {
            if (offline) throw IOException("Unable to resolve host") else MockResponse("[]")
        }
        val source = SupabaseServiceLogRemoteDataSource(harness.postgrest)
        runCatching { source.fetchSince("owner-1", null) }
        offline = false
        source.fetchSince("owner-1", null)

        assertEquals(2, harness.analyticsEvents.size)
        val problems = harness.analyticsEvents.flatMap { (name, props) -> supabaseAnalyticsEvents.problemsWith(name, props) }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
