package com.hopcape.odo.infrastructure.ai

import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.infrastructure.ai.observability.AiTelemetry
import com.hopcape.odo.infrastructure.ai.parsing.BillTextParser
import org.koin.dsl.module

/**
 * The on-device scan adapters.
 *
 * Listed **after** `coreDataModule` in `initKoin` — Koin's later-definition-wins is what
 * retires its `UnconfiguredBillExtractor`. The document and allowance stubs from
 * `:core:data` stay in force: documents have no on-device reader yet, and the allowance
 * is the free tier until entitlements exist.
 *
 * Only this `val` is public. The extractor, parser and telemetry stay `internal`: the
 * rest of the app knows the ports, not who implements them.
 */
val aiInfrastructureModule = module {

    // One facade for every AI call's observability. A `single`: it holds no per-call
    // state — the trace comes from the calling coroutine, not from this object.
    single { AiTelemetry(logger = get(), tracer = get(), crash = get()) }

    // The rules that turn OCR text into a bill. Stateless, and the tested half of the
    // on-device path.
    single { BillTextParser() }

    // Platform-decided: ML Kit on Android, unavailable on iOS until a reader exists.
    // See BillExtractorBinding.kt.
    single<BillExtractor> { platformBillExtractor() }
}
