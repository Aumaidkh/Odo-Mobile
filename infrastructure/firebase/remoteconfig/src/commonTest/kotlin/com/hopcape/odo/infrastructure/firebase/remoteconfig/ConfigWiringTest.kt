package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.config.ChainedConfigSource
import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.config.ConfigResolver
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.core.config.coreConfigModule
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

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
    fun `this module contributes Remote Config under its own qualifier`() {
        // Never as the plain ConfigSource. That is coreConfigModule's, and it holds the
        // chain over every qualified backend — binding the plain interface here is what
        // let this module and supabaseConfigModule overwrite each other depending on which
        // way round initKoin listed them.
        val koin = graph()

        assertNotNull(koin.get<ConfigSource>(named(ConfigSource.REMOTE_CONFIG)))
        assertIs<ChainedConfigSource>(koin.get<ConfigSource>())
        assertIs<ChainedConfigSource>(koin.get<ConfigRefresher>())
    }

    @Test
    fun `the source and the refresher are the same instance`() {
        // The generation counter lives in the instance. Two definitions would mean the
        // graph fetches on one object while every config flow watches the other.
        val koin = graph()

        assertSame<Any>(koin.get<ConfigSource>(), koin.get<ConfigRefresher>())
    }

    @Test
    fun `refreshing the graph fetches Remote Config`() {
        // The app-status gate depends on this and nothing else does it: fetchAndActivate is
        // what moves lastFetchStatus off NoFetchYet, and RemoteConfigAppStatusSource returns
        // null — fail open, no gate — for as long as lastFetchAt is null. A graph whose
        // refresher never touches Firebase is a build where force-update and maintenance mode
        // cannot be switched on at all.
        val gateway = FakeGateway()
        val koin = startKoin {
            modules(
                module { single<Logger> { SilentLogger } },
                coreConfigModule,
                firebaseRemoteConfigModule,
                // Only Firebase itself is faked. The wiring under test is the real one.
                module { single<FirebaseRemoteConfigGateway> { gateway } },
            )
        }.koin

        runTest { koin.get<ConfigRefresher>().refresh() }

        assertEquals(1, gateway.fetches)
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
                "challan_check_enabled",
                "plate_lookup_enabled",
                "advisory_classifier_enabled",
                "bill_check_enabled",
                "service_checklist_enabled",
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
    fun `without either backend the chain is empty and every key reads as null`() {
        // iOS today, and any build with no Firebase project configured. An empty chain is
        // the right answer rather than a failure: every key falls to its compiled default.
        val koin = startKoin { modules(coreConfigModule) }.koin

        assertNull(koin.get<ConfigSource>().string("maintenance_message"))
        assertSame<Any>(koin.get<ConfigSource>(), koin.get<ConfigRefresher>())
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
