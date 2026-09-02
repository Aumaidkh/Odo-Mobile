package com.hopcape.odo.infrastructure.supabase.config

import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigSnapshotStore
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Binds the config system to the `app_config` table.
 *
 * **A module of its own, and its position in `initKoin` is the wiring.** Koin lets a
 * later definition win, so this has to be listed *after* `coreConfigModule` — that
 * module binds `NoRemoteConfigSource` and `ConfigRefresher.None` as the no-backend
 * defaults, and it is itself listed after `supabaseModule` because it must come after
 * everything that registers a `ConfigContribution`. Binding this inside
 * `supabaseModule` therefore looked right and did nothing: the defaults overrode it
 * a few lines later, silently, and every flag would have resolved to its compiled
 * value forever.
 *
 * Firebase Remote Config is not replaced wholesale — `firebaseRemoteConfigModule`
 * still supplies `AppStatusSource`, which reads its own key and has nothing to do
 * with feature flags.
 */
internal fun supabaseConfigModule(environment: SupabaseEnvironment) = module {

    // Behind `isConfigured` like every other Supabase binding: a build with no
    // credentials keeps coreConfigModule's no-backend defaults rather than binding a
    // source whose every read can only fail.
    if (environment.isConfigured) {
        single {
            SupabaseConfigSource(
                postgrest = get(),
                // Null on a platform with no store bound. Falling back to None rather
                // than failing the graph: remembering nothing across launches is a
                // degraded config cache, not a broken app.
                store = getOrNull() ?: ConfigSnapshotStore.None,
            )
        } binds arrayOf(ConfigSource::class, ConfigRefresher::class)
    }
}

/**
 * The module, configured from the build.
 *
 * The same shape as `supabaseModule`: one public value read from `BuildConfig`, and an
 * internal factory the tests can hand a different environment to.
 */
val supabaseConfigModule = supabaseConfigModule(SupabaseEnvironment.fromBuild())
