package com.hopcape.odo.infrastructure.supabase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one decision the environment makes: does this build have a server, or does it run on
 * the fakes? `supabaseModule` binds the real adapters on that answer alone.
 */
class SupabaseEnvironmentTest {

    @Test
    fun `a checkout with no credentials is not configured`() {
        assertFalse(SupabaseEnvironment(url = "", anonKey = "").isConfigured)
    }

    @Test
    fun `half a configuration is no configuration`() {
        assertFalse(SupabaseEnvironment(url = "https://project.supabase.co", anonKey = "").isConfigured)
        assertFalse(SupabaseEnvironment(url = "", anonKey = "anon-key").isConfigured)
        assertFalse(SupabaseEnvironment(url = "   ", anonKey = "anon-key").isConfigured)
    }

    @Test
    fun `both halves present means the adapters take over`() {
        assertTrue(SupabaseEnvironment("https://project.supabase.co", "anon-key").isConfigured)
    }

    @Test
    fun `a trailing slash does not become a double slash in the base URLs`() {
        val environment = SupabaseEnvironment(url = "https://project.supabase.co/", anonKey = "anon-key")

        assertEquals("https://project.supabase.co/rest/v1", environment.restUrl)
        assertEquals("https://project.supabase.co/storage/v1", environment.storageUrl)
    }
}
