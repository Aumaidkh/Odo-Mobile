package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.config.ConfigResolver
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.core.config.NoRemoteConfigSource
import com.hopcape.odo.core.config.coreConfigModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The two things about this wiring that compile either way and only fail at runtime.
 *
 * Both are silent failures: the app would start, every key would answer with its
 * compiled default forever, and nothing would be logged.
 */
class ConfigWiringTest {

    @AfterTest
    fun tearDown() = stopKoin()

    private fun graph() = startKoin {
        modules(
            module { single<Logger> { SilentLogger } },
            coreConfigModule,
            // Listed after, exactly as initKoin lists it.
            firebaseRemoteConfigModule,
        )
    }.koin

    @Test
    fun `this module no longer supplies the ConfigSource`() {
        // Feature flags moved to the `app_config` table; supabaseConfigModule binds the
        // source now, listed immediately after coreConfigModule. This module keeps the
        // app-status gate and nothing else about config.
        //
        // Asserted rather than deleted, because a RemoteConfigSource binding put back
        // here would be listed *after* supabaseConfigModule in initKoin and would
        // silently win — flags would go back to reading an empty Remote Config template
        // and every key would resolve to its compiled default with nothing to show why.
        val koin = graph()

        assertSame(NoRemoteConfigSource, koin.get<ConfigSource>())
        assertSame(ConfigRefresher.None, koin.get<ConfigRefresher>())
    }

    @Test
    fun `every declared key reaches the registry`() {
        // getAll<ConfigContribution>() is resolved lazily, after every module is loaded.
        // If that were not true, the registry would be short some groups and those keys
        // would answer with their compiled defaults forever, silently.
        val keys = graph().get<ConfigRegistry>().keys.map { it.key }.toSet()

        assertEquals(
            setOf(
                // Declared in this module.
                "min_supported_version_code",
                "maintenance_mode",
                "maintenance_message",
                "legal_privacy_policy_url",
                "legal_terms_url",
                "legal_delete_account_url",
                "support_email",
                // Declared in :core:config and reaching the registry from another module,
                // which is the whole point of collecting contributions rather than listing
                // keys somewhere.
                "auto_odometer_enabled",
                "refuel_detect_enabled",
            ),
            keys,
        )
    }

    @Test
    fun `no key is declared twice`() {
        assertEquals(emptyList(), graph().get<ConfigRegistry>().duplicateKeys)
    }

    @Test
    fun `the resolver is reachable`() {
        assertNotNull(graph().get<ConfigResolver>())
    }

    @Test
    fun `without the Firebase module the no-backend source answers`() {
        // iOS today, and any build with no Firebase project configured.
        val koin = startKoin { modules(coreConfigModule) }.koin

        assertSame(NoRemoteConfigSource, koin.get<ConfigSource>())
        assertSame(ConfigRefresher.None, koin.get<ConfigRefresher>())
    }

    private object SilentLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit

        override fun flush() = Unit
    }
}
