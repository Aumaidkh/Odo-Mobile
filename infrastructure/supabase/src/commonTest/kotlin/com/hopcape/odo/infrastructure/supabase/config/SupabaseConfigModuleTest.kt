package com.hopcape.odo.infrastructure.supabase.config

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigSnapshotStore
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.core.config.NoRemoteConfigSource
import com.hopcape.odo.core.config.coreConfigModule
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.infrastructure.supabase.NoopTracer
import com.hopcape.odo.infrastructure.supabase.RecordingCrashRecorder
import com.hopcape.odo.infrastructure.supabase.RecordingLogger
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.infrastructure.supabase.supabaseModule
import com.hopcape.performance.api.PerformanceTracer
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Where this module is listed *is* the wiring, so that is what is tested.
 *
 * `coreConfigModule` binds `NoRemoteConfigSource` and `ConfigRefresher.None` as its
 * no-backend defaults, and it has to be listed after every module that registers a
 * `ConfigContribution` — which puts it after `supabaseModule`. Koin lets a later
 * definition win, so binding the real source inside `supabaseModule` compiles, reads
 * correctly, and does nothing at all: the defaults overwrite it a few lines later and
 * every flag resolves to its compiled value forever, with nothing to show why.
 *
 * That is the failure these tests exist to catch, and it is invisible in any test that
 * loads the modules in a different order from `initKoin`.
 */
class SupabaseConfigModuleTest {

    private val configured = SupabaseEnvironment(url = "https://project.supabase.co", anonKey = "anon-key")
    private val unconfigured = SupabaseEnvironment(url = "", anonKey = "")

    @Test
    fun `listed after coreConfigModule, the table-backed source wins`() {
        val koin = graph(configured, configLast = true)

        assertIs<SupabaseConfigSource>(
            koin.get<ConfigSource>(),
            "expected SupabaseConfigSource. Listing supabaseConfigModule before " +
                "coreConfigModule puts the no-backend source back, and every flag stays " +
                "on its compiled default no matter what the panel is set to.",
        )
    }

    @Test
    fun `listed before coreConfigModule, it is silently overridden`() {
        // Not a supported arrangement — asserted so the trap is written down as an
        // executable fact rather than as a comment somebody can move code past.
        val koin = graph(configured, configLast = false)

        assertSame(NoRemoteConfigSource, koin.get<ConfigSource>())
    }

    @Test
    fun `the source and the refresher are the same object`() {
        // The generation counter lives in the instance. Two definitions would mean the
        // refresher bumps one object while every flow watches another, so no screen
        // would ever update after a refresh.
        val koin = graph(configured, configLast = true)

        assertSame(koin.get<ConfigSource>(), koin.get<ConfigRefresher>() as Any)
    }

    @Test
    fun `an unconfigured build keeps the no-backend default`() {
        // A source whose every read can only fail is worse than none: it would report
        // "no remote value" identically but spend a request finding out.
        val koin = graph(unconfigured, configLast = true)

        assertSame(NoRemoteConfigSource, koin.get<ConfigSource>())
        assertSame(ConfigRefresher.None, koin.get<ConfigRefresher>())
    }

    @Test
    fun `a graph with no snapshot store still builds`() {
        // getOrNull falls back to ConfigSnapshotStore.None. Remembering nothing across
        // launches is a degraded cache, not a reason to fail the graph at startup.
        val koin = graph(configured, configLast = true, withStore = false)

        assertTrue(koin.get<ConfigSource>() is SupabaseConfigSource)
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
