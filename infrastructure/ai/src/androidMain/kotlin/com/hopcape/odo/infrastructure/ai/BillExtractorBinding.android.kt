package com.hopcape.odo.infrastructure.ai

import com.hopcape.odo.core.domain.scan.BillExtractor
import org.koin.core.scope.Scope

/** Android reads bills on-device for now — see the expect's KDoc for the why. */
internal actual fun Scope.platformBillExtractor(): BillExtractor =
    MlKitBillExtractor(files = get(), parser = get(), telemetry = get())
