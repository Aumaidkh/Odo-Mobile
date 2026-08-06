package com.hopcape.odo.infrastructure.ai

import com.hopcape.odo.core.domain.scan.DocumentExtractor
import org.koin.core.scope.Scope

/** Android reads papers on-device — see the expect's KDoc for the why. */
internal actual fun Scope.platformDocumentExtractor(): DocumentExtractor =
    MlKitDocumentExtractor(files = get(), parser = get(), telemetry = get())
