package com.hopcape.odo.infrastructure.ai

import com.hopcape.odo.core.domain.scan.PumpReadingExtractor
import org.koin.core.scope.Scope

/** Android reads the pump display on-device — see the expect's KDoc for the why. */
internal actual fun Scope.platformPumpReadingExtractor(): PumpReadingExtractor =
    MlKitPumpReadingExtractor(files = get(), parser = get(), telemetry = get())
