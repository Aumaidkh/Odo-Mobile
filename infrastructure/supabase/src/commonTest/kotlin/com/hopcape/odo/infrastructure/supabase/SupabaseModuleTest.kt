package com.hopcape.odo.infrastructure.supabase

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.data.document.DocumentRemoteDataSource
import com.hopcape.odo.core.data.fairness.FairnessRemoteDataSource
import com.hopcape.odo.core.data.fairness.OverchargeRemoteDataSource
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseDocumentRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseFairnessRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseOverchargeRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseRemoteFileStorage
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import com.hopcape.performance.api.PerformanceTracer
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The one thing `supabaseModule` decides: whether this build talks to a server or stays on
 * `:core:data`'s offline fakes. Both branches are checked here rather than left to whatever
 * happened to be in the developer's `local.properties`.
 */
class SupabaseModuleTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun `with credentials, every remote port resolves to a Supabase adapter`() {
        val koin = graph(SupabaseEnvironment(url = "https://project.supabase.co", anonKey = "anon-key"))

        assertIs<SupabaseServiceLogRemoteDataSource>(koin.get<ServiceLogRemoteDataSource>())
        assertIs<SupabaseDocumentRemoteDataSource>(koin.get<DocumentRemoteDataSource>())
        assertIs<SupabaseFairnessRemoteDataSource>(koin.get<FairnessRemoteDataSource>())
        assertIs<SupabaseOverchargeRemoteDataSource>(koin.get<OverchargeRemoteDataSource>())
        assertIs<SupabaseRemoteFileStorage>(koin.get<RemoteFileStorage>())
    }

    @Test
    fun `without credentials, no port is claimed, so coreDataModule's fakes stand`() {
        val koin = graph(SupabaseEnvironment(url = "", anonKey = ""))

        // Nothing bound here means the definition from `coreDataModule` — listed earlier in
        // `initKoin` — is the one that survives. An override that never happens is the point.
        assertNull(koin.getOrNull<ServiceLogRemoteDataSource>())
        assertNull(koin.getOrNull<DocumentRemoteDataSource>())
        assertNull(koin.getOrNull<FairnessRemoteDataSource>())
        assertNull(koin.getOrNull<OverchargeRemoteDataSource>())
        assertNull(koin.getOrNull<RemoteFileStorage>())
    }

    @Test
    fun `the protocol clients are always available, configured or not`() {
        // They are lazy singles, so an unconfigured build defines them without ever building
        // an HTTP client. Resolving one here proves the wiring, not that it happens at startup.
        assertNotNull(graph(SupabaseEnvironment("", "")).get<PostgrestClient>())
    }

    /** `supabaseModule` plus the observability doubles its telemetry facade needs. */
    private fun graph(environment: SupabaseEnvironment) = koinApplication {
        modules(
            module {
                single<Logger> { RecordingLogger }
                single<PerformanceTracer> { NoopTracer }
                single<CrashRecorder> { RecordingCrashRecorder }
            },
            supabaseModule(environment),
        )
    }.koin
}
