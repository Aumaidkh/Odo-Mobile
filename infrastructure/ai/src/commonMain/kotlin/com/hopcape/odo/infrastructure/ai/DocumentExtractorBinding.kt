package com.hopcape.odo.infrastructure.ai

import com.hopcape.odo.core.domain.scan.DocumentExtractor
import org.koin.core.scope.Scope

/**
 * Which [DocumentExtractor] this platform runs — the document twin of
 * [platformBillExtractor], and split for the same reason.
 *
 * Papers are read **on-device with ML Kit**: an insurance certificate or a licence carries
 * a name and a number, so keeping the photo on the phone is not only cheaper than a backend,
 * it is the answer to what happens to it. Android binds [MlKitDocumentExtractor]; iOS
 * answers unavailable until it has a reader of its own, which the review screen already
 * handles by leaving the dates typeable.
 */
internal expect fun Scope.platformDocumentExtractor(): DocumentExtractor
