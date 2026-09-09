package com.hopcape.odo.infrastructure.supabase.config

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.config.ChainedConfigSource
import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigSnapshotStore
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.core.config.coreConfigModule
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.infrastructure.supabase.NoopTracer
import com.hopcape.odo.infrastructure.supabase.RecordingCrashRecorder
import com.hopcape.odo.infrastructure.supabase.RecordingLogger
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.infrastructure.supabase.supabaseModule
import com.hopcape.performance.api.PerformanceTracer
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * This module contributes one backend to the config chain, and these tests pin the two
 * things about that which compile either way and only fail at runtime.
 *
 * Where the module is listed used to *be* the wiring: both adapters bound the plain
 * `ConfigSource` and Koin let the later definition win, so whichever was listed second
 * silently erased the other. Each binds its own qualifier now and `coreConfigModule`
 * assembles the chain, so the order is no longer load-bearing — which is what the first
 * test asserts, both ways round.
 */
class SupabaseConfigModuleTest {

    private val configured = SupabaseEnvironment(url = "https://project.supabase.co", anonKey = "anon-key")
    private val unconfigured = SupabaseEnvironment(url = "", anonKey = "")

    @Test
    fun `the table-backed source joins the chain whichever way round the modules are listed`() {
        listOf(true, false).forEach { configLast ->
            val koin = graph(configured, configLast = configLast)

            assertIs<SupabaseConfigSource>(
                koin.getOrNull<ConfigSource>(named(ConfigSource.APP_CONFIG_TABLE)),
                "expected the app_config source to be contributed with configLast=$configLast. " +
                    "Binding the plain ConfigSource here instead of the qualifier is what let " +
                    "the two adapters overwrite each other.",
            )
            assertIs<ChainedConfigSource>(koin.get<ConfigSource>())
        }
    }

    @Test
    fun `the source and the refresher are the same object`() {
        // The generation counter lives in the instance. Two definitions would mean the
        // refresher bumps one object while every flow watches another, so no screen
        // would ever update after a refresh.
        val koin = graph(configured, configLast = true)

        assertSame<Any>(koin.get<ConfigSource>(), koin.get<ConfigRefresher>())
    }

    @Test
    fun `an unconfigured build contributes nothing to the chain`() {
        // A source whose every read can only fail is worse than none: it would report
        // "no remote value" identically but spend a request finding out.
        val koin = graph(unconfigured, configLast = true)

        assertNull(koin.getOrNull<ConfigSource>(named(ConfigSource.APP_CONFIG_TABLE)))
        assertIs<ChainedConfigSource>(koin.get<ConfigSource>())
    }

    @Test
    fun `a graph with no snapshot store still builds`() {
        // getOrNull falls back to ConfigSnapshotStore.None. Remembering nothing across
        // launches is a degraded cache, not a reason to fail the graph at startup.
        val koin = graph(configured, configLast = true, withStore = false)

        assertIs<SupabaseConfigSource>(koin.getOrNull<ConfigSource>(named(ConfigSource.APP_CONFIG_TABLE)))
    }

    private fun graph(
        environment: SupabaseEnvironment,
        configLast: Boolean,
        withStore: Boolean = true,
    ) = koinApplication {
        val base: Module = module {
            single<Logger> { RecordingLogger }
            single<PerformanceTracer> { NoopTracer }
            single<CrashRecorder> { RecordingCrashRecorder }
            single<AccessTokenProvider> { AccessTokenProvider { null } }
            if (withStore) single<ConfigSnapshotStore> { ConfigSnapshotStore.None }
        }
        val ordered = if (configLast) {
            listOf(base, supabaseModule(environment), coreConfigModule, supabaseConfigModule(environment))
        } else {
            listOf(base, supabaseModule(environment), supabaseConfigModule(environment), coreConfigModule)
        }
        modules(ordered)
    }.koin
}
