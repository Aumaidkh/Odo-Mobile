package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.common.runCatchingCancellable
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.FetchStatus
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlin.time.Instant

// ─────────────────────────────────────────────────────────────
// FirebaseRemoteConfigGateway — the real Firebase SDK surface this module calls,
// narrowed to what RemoteConfigAppStatusSource needs. The seam exists because
// gitlive's FirebaseRemoteConfig is a concrete SDK class (an expect class), not an
// interface, so nothing about it can be faked directly in a test — this is the
// fake-able boundary instead. Mirrors :infrastructure:firebase:analytics's
// FirebaseAnalyticsGateway / :infrastructure:firebase:crashlytics's
// FirebaseCrashlyticsGateway.
// ─────────────────────────────────────────────────────────────
internal interface FirebaseRemoteConfigGateway {

    /**
     * Fetches and activates the latest config, subject to the SDK's own throttling. The
     * return value says whether a *new* config was activated — callers read [long]/[string]
     * regardless, since a throttled call still serves whatever was last actually fetched.
     */
    suspend fun fetchAndActivate(): Boolean

    /** The current value of [key] as a `Long`, or `null` if it could not be read. */
    fun long(key: String): Long?

    /** The current value of [key] as a `String`, or `null` if it could not be read. */
    fun string(key: String): String?

    /**
     * When the config currently in force was genuinely confirmed from Firebase — not "now",
     * but the SDK's own record of its last *successful* network fetch, which still applies
     * even when the most recent [fetchAndActivate] call was throttled. `null` before the
     * very first successful fetch this install has ever made.
     */
    val lastFetchAt: Instant?
}

/**
 * `Firebase.remoteConfig` throws when no `FirebaseApp` has been configured — a missing or
 * not-yet-added `google-services.json`/`GoogleService-Info.plist`, which this repo's own
 * config files currently are. Resolution is lazy (not a constructor default) and every call
 * is caught (via [runCatchingCancellable]/[runCatchingCancellableSuspend], never
 * `runCatching` — that would also swallow a coroutine's cancellation), so a misconfigured
 * Firebase project degrades this source — via [onDiagnostic] — instead of crashing app
 * launch, matching the "a vendor SDK can never crash the host" guarantee the rest of the
 * observability pipeline holds. This is the app-status gate's equivalent: an unreadable
 * remote source must fail open, never crash.
 *
 * [minimumFetchIntervalSeconds] and [defaults] are applied once, lazily, on first use — the
 * SDK persists them for the process, so re-applying on every call would be redundant work,
 * not a correctness issue.
 *
 * [provider] exists only so a test can inject a throwing lookup without a real Firebase
 * project — production always uses the default.
 */
internal class RealFirebaseRemoteConfigGateway(
    private val minimumFetchIntervalSeconds: Long,
    private val defaults: Map<String, Any>,
    private val onDiagnostic: (String) -> Unit = {},
    private val provider: () -> FirebaseRemoteConfig = { Firebase.remoteConfig },
) : FirebaseRemoteConfigGateway {

    private val remoteConfig: FirebaseRemoteConfig? by lazy {
        runCatchingCancellable(provider)
            .onFailure { onDiagnostic("remoteconfig: unavailable — ${it::class.simpleName}") }
            .getOrNull()
    }

    private var configured = false

    private suspend fun configured(config: FirebaseRemoteConfig): FirebaseRemoteConfig {
        if (configured) return config
        runCatchingCancellableSuspend {
            config.settings { minimumFetchIntervalInSeconds = minimumFetchIntervalSeconds }
            config.setDefaults(*defaults.map { (key, value) -> key to value }.toTypedArray())
        }.onFailure { onDiagnostic("remoteconfig: configure failed — ${it::class.simpleName}") }
        // Set regardless of the outcome above: a failed configure is not worth retrying on
        // every call, and fetchAndActivate below still degrades safely without defaults set.
        configured = true
        return config
    }

    override suspend fun fetchAndActivate(): Boolean {
        val config = remoteConfig?.let { configured(it) } ?: return false
        return runCatchingCancellableSuspend { config.fetchAndActivate() }
            .onFailure { onDiagnostic("remoteconfig: fetchAndActivate failed — ${it::class.simpleName}") }
            .getOrDefault(false)
    }

    override fun long(key: String): Long? {
        val config = remoteConfig ?: return null
        return runCatchingCancellable { config.getValue(key).asLong() }
            .onFailure { onDiagnostic("remoteconfig: read '$key' failed — ${it::class.simpleName}") }
            .getOrNull()
    }

    override fun string(key: String): String? {
        val config = remoteConfig ?: return null
        return runCatchingCancellable { config.getValue(key).asString() }
            .onFailure { onDiagnostic("remoteconfig: read '$key' failed — ${it::class.simpleName}") }
            .getOrNull()
    }

    override val lastFetchAt: Instant?
        get() {
            val config = remoteConfig ?: return null
            return runCatchingCancellable { config.info }
                .getOrNull()
                ?.takeIf { it.lastFetchStatus != FetchStatus.NoFetchYet }
                ?.fetchTime
        }
}
