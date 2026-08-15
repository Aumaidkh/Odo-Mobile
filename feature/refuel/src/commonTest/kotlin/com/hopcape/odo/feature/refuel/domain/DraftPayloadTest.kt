package com.hopcape.odo.feature.refuel.domain

import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.shared.Amount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The draft's trip through a notification and back.
 *
 * Worth its own suite because the failure mode is invisible: a draft that decodes wrongly
 * writes a fill with the wrong numbers, and the owner tapped Confirm on a notification that
 * showed the right ones.
 */
class DraftPayloadTest {

    @Test
    fun aFullDraftSurvivesTheRoundTrip() {
        val draft = FuelFillDraft(
            source = FillEntrySource.DETECTED,
            unit = FuelUnit.LITRE,
            amount = Amount.of(200_000).getOrNull(),
            amountOrigin = FieldOrigin.PAYMENT,
            quantityMilli = 21_119,
            quantityOrigin = FieldOrigin.DERIVED,
            pricePerUnit = Amount.of(9_470).getOrNull(),
            priceOrigin = FieldOrigin.HISTORY,
            odometerKm = 34_612,
            odometerOrigin = FieldOrigin.PREDICTED,
            stationName = "Bharat Petroleum, Karol Bagh",
        )

        assertEquals(draft, DraftPayload.decode(DraftPayload.encode(draft)))
    }

    @Test
    fun absentFieldsStayAbsent() {
        val draft = FuelFillDraft(
            source = FillEntrySource.MANUAL,
            amount = Amount.of(150_000).getOrNull(),
        )

        val decoded = DraftPayload.decode(DraftPayload.encode(draft))

        assertEquals(draft, decoded)
        assertNull(decoded?.quantityMilli)
        assertNull(decoded?.stationName)
    }

    @Test
    fun aMerchantNameKeepsItsPunctuation() {
        // Commas and full stops are ordinary in a station name, and a delimiter that could
        // appear inside one would shift every field after it.
        val draft = FuelFillDraft(
            source = FillEntrySource.DETECTED,
            stationName = "H.P. Petrol Pump, Sector 22, Chandigarh",
        )

        assertEquals(
            "H.P. Petrol Pump, Sector 22, Chandigarh",
            DraftPayload.decode(DraftPayload.encode(draft))?.stationName,
        )
    }

    @Test
    fun aTruncatedPayloadDecodesToNothingRatherThanThrowing() {
        assertNull(DraftPayload.decode("DETECTED"))
        assertNull(DraftPayload.decode(""))
    }

    @Test
    fun aPayloadNamingSomethingThisBuildDoesNotKnowDecodesToNothing() {
        // An intent written by a newer build, held by the system across an update.
        val draft = FuelFillDraft(source = FillEntrySource.DETECTED)
        val corrupted = DraftPayload.encode(draft).replace("DETECTED", "TELEPATHY")

        assertNull(DraftPayload.decode(corrupted))
    }
}
