package com.hopcape.odo.feature.servicelog.presentation.share.pdf

import com.hopcape.odo.core.designsystem.theme.BrandFont
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A built document: the markup to print, and what the file should be called. */
internal data class ServiceRecordDocument(val html: String, val name: String)

/**
 * Turns a record into the document that gets printed.
 *
 * A seam rather than a direct call, because building one means reading string resources and
 * font files — both of which need an Android runtime underneath them. Behind this interface,
 * the share ViewModel's rules (render once, reuse, fail loudly) can be tested on the host
 * JVM, which is where they belong.
 */
internal fun interface ServiceRecordDocumentFactory {
    suspend fun create(record: ServiceRecord): ServiceRecordDocument
}

/**
 * The real one: the feature's own copy, the brand face, and [ServiceRecordHtml].
 *
 * The fonts are read and encoded on every call rather than held. A document is built once
 * per share sheet, and keeping a quarter of a megabyte of base64 alive for the life of the
 * process to save that is the wrong trade.
 */
internal class BrandedServiceRecordDocumentFactory : ServiceRecordDocumentFactory {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun create(record: ServiceRecord): ServiceRecordDocument {
        val labels = ServiceRecordLabels.load()
        val fonts = ServiceRecordHtml.Fonts(
            regularBase64 = Base64.encode(BrandFont.regularBytes()),
            boldBase64 = Base64.encode(BrandFont.boldBytes()),
        )

        return ServiceRecordDocument(
            html = ServiceRecordHtml.build(record, labels, fonts),
            name = labels.documentTitle(record.carName),
        )
    }
}
