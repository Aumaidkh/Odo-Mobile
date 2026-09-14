package com.hopcape.odo.infrastructure.supabase.config

import com.hopcape.odo.core.config.ConfigSnapshotStore
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Contributes the `app_config` table to the config chain, behind Firebase Remote Config.
 *
 * **A module of its own, but its position in `initKoin` no longer decides anything.** It
 * binds under [ConfigSource.APP_CONFIG_TABLE], never the plain `ConfigSource`, and
 * `coreConfigModule` assembles the chain from whichever qualified backends are present.
 * Both adapters used to bind the plain interface and silently overwrite each other,
 * whichever way round the list happened to be.
 *
 * Remote Config answers a key it holds; this answers the rest. The admin panel therefore
 * still governs every flag nobody has set in the Firebase console.
 */
internal fun supabaseConfigModule(environment: SupabaseEnvironment) = module {

    // Behind `isConfigured` like every other Supabase binding: a build with no
    // credentials contributes nothing to the chain rather than a backend whose every
    // read can only fail.
    if (environment.isConfigured) {
        single<ConfigSource>(named(ConfigSource.APP_CONFIG_TABLE)) {
            SupabaseConfigSource(
                postgrest = get(),
                // Null on a platform with no store bound. Falling back to None rather
                // than failing the graph: remembering nothing across launches is a
                // degraded config cache, not a broken app.
                store = getOrNull() ?: ConfigSnapshotStore.None,
            )
        }
    }
}

/**
 * The module, configured from the build.
 *
 * The same shape as `supabaseModule`: one public value read from `BuildConfig`, and an
 * internal factory the tests can hand a different environment to.
 */
val supabaseConfigModule = supabaseConfigModule(SupabaseEnvironment.fromBuild())
