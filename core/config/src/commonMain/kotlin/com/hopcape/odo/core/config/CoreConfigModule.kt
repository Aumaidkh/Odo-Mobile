package com.hopcape.odo.core.config

import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.BuildInfo
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The registry, the resolver, and the chain of backends they read through.
 *
 * **Ordering.** List this after every module that registers a [ConfigContribution]. Where
 * the backend adapters sit no longer matters: each binds itself under its own qualifier
 * ([ConfigSource.REMOTE_CONFIG], [ConfigSource.APP_CONFIG_TABLE]) and the chain below picks
 * up whichever are present, resolved lazily once every module is loaded. Two adapters used
 * to bind the plain [ConfigSource] and quietly overwrite one another, which is how Remote
 * Config stopped being fetched at all.
 *
 * [LocalConfigOverrides] is resolved with `getOrNull`, so a release build simply has no
 * store behind it. Nothing about the resolution order changes between variants; there is
 * only nothing to find.
 */
val coreConfigModule = module {

    // FeatureConfig, declared in this module. Included here so a consumer gets it from
    // coreConfigModule and initKoin's list does not grow with every group.
    includes(featureConfigModule)

    // Remote Config first, then the app_config table, then the compiled default. A build
    // with neither adapter gets an empty chain, which answers null to everything — the
    // correct behaviour for a build with no backend, not a stub to delete later.
    //
    // One instance bound to both interfaces, not two definitions: the generation counter
    // lives in it, so a second instance would fetch on one object and leave every flow
    // watching the other.
    single {
        ChainedConfigSource(
            sources = listOfNotNull(
                getOrNull<ConfigSource>(named(ConfigSource.REMOTE_CONFIG)),
                getOrNull<ConfigSource>(named(ConfigSource.APP_CONFIG_TABLE)),
            ),
            onRefreshFailure = { e ->
                get<Logger>().warn(TAG, "config refresh failed — ${e::class.simpleName}")
            },
        )
    } binds arrayOf(ConfigSource::class, ConfigRefresher::class)

    // getAll, so a module that declares config contributes by being installed and nothing
    // has to maintain a list. Resolved lazily, after every module is loaded.
    single {
        ConfigRegistry(getAll<ConfigContribution>()).also { registry ->
            enforceUniqueKeys(
                registry = registry,
                isDebug = BuildInfo.isDebug,
                onWarn = { message -> get<Logger>().warn(TAG, message) },
            )
        }
    }

    single { ConfigResolver(registry = get(), source = get(), overrides = getOrNull()) }
}

private const val TAG = "Config"
