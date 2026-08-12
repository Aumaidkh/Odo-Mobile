package com.hopcape.odo.feature.garage.presentation.sheets.pdf

import com.hopcape.odo.core.designsystem.theme.BrandFont
import com.hopcape.odo.feature.garage.domain.usecase.CarDetails
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A built document: the markup to print, and what the file should be called. */
internal data class CarDetailsPrintable(val html: String, val name: String)

/**
 * Turns the car's details into the document that gets printed — the garage's counterpart
 * of the service log's document factories, and a seam for the same reason: building one
 * reads string resources and font files, both of which need an Android runtime underneath
 * them. Behind this interface the export ViewModel stays testable on the host JVM.
 */
internal fun interface CarDetailsDocumentFactory {
    suspend fun create(details: CarDetails): CarDetailsPrintable
}

/**
 * The real one: the feature's own copy, the brand face, and [CarDetailsHtml]. The fonts
 * are read and encoded per call — one document per sheet is not worth a quarter of a
 * megabyte of base64 held for the life of the process.
 */
internal class BrandedCarDetailsDocumentFactory : CarDetailsDocumentFactory {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun create(details: CarDetails): CarDetailsPrintable {
        val labels = CarDetailsLabels.load()
        val fonts = CarDetailsHtml.Fonts(
            regularBase64 = Base64.encode(BrandFont.regularBytes()),
            boldBase64 = Base64.encode(BrandFont.boldBytes()),
        )

        return CarDetailsPrintable(
            html = CarDetailsHtml.build(details, labels, fonts),
            name = labels.documentTitle(details.record.carName),
        )
    }
}
