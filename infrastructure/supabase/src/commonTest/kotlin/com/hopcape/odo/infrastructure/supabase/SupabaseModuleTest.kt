package com.hopcape.odo.infrastructure.supabase

import arrow.core.right
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.data.cost.FuelFillRemoteDataSource
import com.hopcape.odo.core.data.document.DocumentRemoteDataSource
import com.hopcape.odo.core.data.fairness.FairnessRemoteDataSource
import com.hopcape.odo.core.data.fairness.OverchargeRemoteDataSource
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.auth.DevPasswordAuthGateway
import com.hopcape.odo.infrastructure.supabase.auth.FirebaseBridgeAuthGateway
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseDocumentRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseFuelFillRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseFairnessRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseLegalLinks
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseOverchargeRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseRemoteFileStorage
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import com.hopcape.performance.api.PerformanceTracer
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
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
        assertIs<SupabaseFuelFillRemoteDataSource>(koin.get<FuelFillRemoteDataSource>())
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
        assertNull(koin.getOrNull<FuelFillRemoteDataSource>())
        assertNull(koin.getOrNull<FairnessRemoteDataSource>())
        assertNull(koin.getOrNull<OverchargeRemoteDataSource>())
        assertNull(koin.getOrNull<RemoteFileStorage>())
    }

    /**
     * The one binding here that reaches outside this module. With phone auth on, the gateway
     * needs a `PhoneVerifier`, which `firebaseAuthModule` publishes earlier in `initKoin` —
     * so a wiring mistake shows up as a missing definition on the sign-in screen and nowhere
     * before it.
     */
    @Test
    fun `with phone auth on, the gateway is the Firebase bridge`() {
        val koin = graph(
            SupabaseEnvironment(url = "https://project.supabase.co", anonKey = "anon-key", usePhoneAuth = true),
            module { single<PhoneVerifier> { StubVerifier } },
        )

        assertIs<FirebaseBridgeAuthGateway>(koin.get<AuthGateway>())
    }

    @Test
    fun `with phone auth off, the development account signs in and no verifier is needed`() {
        val koin = graph(SupabaseEnvironment(url = "https://project.supabase.co", anonKey = "anon-key"))

        // No PhoneVerifier in this graph at all. Resolving proves the dev branch does not
        // reach for one — a build with no Firebase set up still signs in.
        assertIs<DevPasswordAuthGateway>(koin.get<AuthGateway>())
    }

    /**
     * Both bindings, because `firebaseRemoteConfigModule` resolves the qualified one by name
     * as the fallback behind the console-configured links — and a rename here would show up
     * only as a missing definition when someone opens the privacy screen.
     */
    @Test
    fun `the legal links resolve under both the plain type and the built-in qualifier`() {
        val koin = graph(SupabaseEnvironment(url = "https://project.supabase.co", anonKey = "anon-key"))

        val qualified = koin.get<LegalLinks>(named(LegalLinks.BUILT_IN))
        assertIs<SupabaseLegalLinks>(qualified)
        // The unqualified one is the same instance: it is what a build with no remote-config
        // module still answers with, not a second copy that could drift.
        assertSame(qualified, koin.get<LegalLinks>())
    }

    @Test
    fun `legal links are bound even without credentials, and answer blank`() {
        // Unlike every other binding here, these are outside the isConfigured branch — three
        // strings with nothing to call. Blank means "not configured", and the screens leave
        // the row out rather than offering a dead link.
        val koin = graph(SupabaseEnvironment("", ""))

        assertEquals("", koin.get<LegalLinks>().privacyPolicy)
    }

    @Test
    fun `the protocol clients are always available, configured or not`() {
        // They are lazy singles, so an unconfigured build defines them without ever building
        // an HTTP client. Resolving one here proves the wiring, not that it happens at startup.
        assertNotNull(graph(SupabaseEnvironment("", "")).get<PostgrestClient>())
    }

    /**
     * `supabaseModule` plus the observability doubles its telemetry facade needs. [extra]
     * stands in for what other modules publish into the real graph.
     */
    private fun graph(environment: SupabaseEnvironment, extra: Module? = null) = koinApplication {
        modules(
            listOfNotNull(
                module {
                    single<Logger> { RecordingLogger }
                    single<PerformanceTracer> { NoopTracer }
                    single<CrashRecorder> { RecordingCrashRecorder }
                    // The session lives in :feature:auth now; this module only consumes the
                    // token through the domain port.
                    single<AccessTokenProvider> { AccessTokenProvider { null } }
                },
                extra,
                supabaseModule(environment),
            )
        )
    }.koin

    private object StubVerifier : PhoneVerifier {
        override suspend fun startVerification(phone: PhoneNumber) = Unit.right()
        override suspend fun submitCode(code: String) = VerifiedPhoneToken("token").right()
        override suspend fun forget() = Unit
    }
}
