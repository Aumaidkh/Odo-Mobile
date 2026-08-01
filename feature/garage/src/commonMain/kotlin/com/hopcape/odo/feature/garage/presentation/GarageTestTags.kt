package com.hopcape.odo.feature.garage.presentation

import com.hopcape.odo.core.domain.document.model.DocumentType

/**
 * Test tags for the garage controls an end-to-end test cannot reach by the words on them.
 *
 * Deliberately few. Copy is what an owner sees, so a test that finds a card by its name is
 * testing the product; a tag is only added where the words repeat. Every document row says
 * "+ Add" or a validity badge, and every service row says a category name that another row
 * may say too, so only the tag says *which* one it is.
 *
 * Public because `:androidApp`'s instrumented tests reference these, which is the only
 * reason anything in this module is public besides the Koin module and the analytics schema.
 */
object GarageTestTags {

    /** The car card — present only once a car has been set up. */
    const val CAR_CARD: String = "garage_car_card"

    /** The odometer reading on that card. */
    const val ODOMETER: String = "garage_odometer"

    /* The car-detail pickers. Each collapses to a field that says only "Choose". */
    const val MAKE_FIELD: String = "garage_make_field"
    const val MODEL_FIELD: String = "garage_model_field"
    const val YEAR_FIELD: String = "garage_year_field"
    const val FUEL_FIELD: String = "garage_fuel_field"
    const val REGISTRATION_FIELD: String = "garage_registration_field"
    const val NICKNAME_FIELD: String = "garage_nickname_field"
    const val ODOMETER_FIELD: String = "garage_odometer_field"

    /** One paper's row in the document overview, on file or missing. */
    fun documentRow(type: DocumentType): String = "garage_document_row_${type.name}"

    /** One logged service's card in the inline history. */
    fun serviceRow(logId: String): String = "garage_service_row_$logId"
}
