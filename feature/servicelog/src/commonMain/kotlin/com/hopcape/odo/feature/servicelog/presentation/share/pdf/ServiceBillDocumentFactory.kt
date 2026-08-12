package com.hopcape.odo.feature.servicelog.presentation.share.pdf

import com.hopcape.odo.core.designsystem.theme.BrandFont
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Turns one entry's bill into the document that gets printed — the single-entry counterpart
 * of [ServiceRecordDocumentFactory], and a seam for the same reason: building one reads
 * string resources and font files, both of which need an Android runtime underneath them.
 * Behind this interface the share ViewModel stays testable on the host JVM.
 *
 * The [ServiceRecord] rides along for the car's identity — the masthead and running header
 * name the car, and the entry does not know what it is called.
 */
internal fun interface ServiceBillDocumentFactory {
    suspend fun create(record: ServiceRecord, entry: ServiceLogEntry): ServiceRecordDocument
}

/**
 * The real one: the feature's own copy, the brand face, and [ServiceBillHtml]. The fonts are
 * read and encoded per call for the same reason as
 * [BrandedServiceRecordDocumentFactory] — one document per sheet is not worth a quarter of a
 * megabyte of base64 held for the life of the process.
 */
internal class BrandedServiceBillDocumentFactory : ServiceBillDocumentFactory {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun create(record: ServiceRecord, entry: ServiceLogEntry): ServiceRecordDocument {
        val labels = ServiceBillLabels.load()
        val fonts = ServiceRecordHtml.Fonts(
            regularBase64 = Base64.encode(BrandFont.regularBytes()),
            boldBase64 = Base64.encode(BrandFont.boldBytes()),
        )

        return ServiceRecordDocument(
            html = ServiceBillHtml.build(record, entry, labels, fonts),
            name = labels.documentTitle(record.carName),
        )
    }
}
