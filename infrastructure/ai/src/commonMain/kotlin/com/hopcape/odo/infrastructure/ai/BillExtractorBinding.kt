package com.hopcape.odo.infrastructure.ai

import com.hopcape.odo.core.domain.scan.BillExtractor
import org.koin.core.scope.Scope

/**
 * Which [BillExtractor] this platform runs — the one place the OCR decision lives.
 *
 * Bills are read **on-device with ML Kit**: no photo leaves the device and no backend
 * needs to exist. ML Kit ships an Android SDK only, and the MVP is Android-only anyway,
 * so Android binds [MlKitBillExtractor] and iOS answers unavailable until it has a
 * reader of its own. Swapping in a different engine later is this one actual — nothing
 * above the port notices.
 */
internal expect fun Scope.platformBillExtractor(): BillExtractor
