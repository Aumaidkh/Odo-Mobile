package com.hopcape.odo.core.common

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.common.id.UuidIdGenerator
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * DI graph for the utilities every layer needs: id generation and the clock.
 *
 * Both used to live in the feature that happened to need them first — [IdGenerator] in
 * onboarding, [Clock] in servicelog. They are here now that a second feature needs them,
 * so no feature depends on another feature's module being registered.
 *
 * Injected rather than called directly ([Clock.System], `Uuid.random()`) so tests can pass
 * a fixed clock and predictable ids.
 */
val coreCommonModule = module {
    single<IdGenerator> { UuidIdGenerator() }
    single<Clock> { Clock.System }
}
