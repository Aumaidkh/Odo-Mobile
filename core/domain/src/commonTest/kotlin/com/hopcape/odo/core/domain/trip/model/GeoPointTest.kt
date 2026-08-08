package com.hopcape.odo.core.domain.trip.model

import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoPointTest {

    @Test
    fun validCoordinate_isAccepted() {
        val result = GeoPoint.of(19.0760, 72.8777)
        assertTrue(result.isRight())
        assertEquals(19.0760, result.getOrNull()?.lat)
        assertEquals(72.8777, result.getOrNull()?.lon)
    }

    @Test
    fun latOutOfRange_isRejected() {
        assertEquals(DomainError.InvalidCoordinate("lat", 91.0), GeoPoint.of(91.0, 0.0).leftOrNull())
    }

    @Test
    fun lonOutOfRange_isRejected() {
        assertEquals(DomainError.InvalidCoordinate("lon", 181.0), GeoPoint.of(0.0, 181.0).leftOrNull())
    }

    @Test
    fun boundaryValues_areAccepted() {
        assertTrue(GeoPoint.of(90.0, 180.0).isRight())
        assertTrue(GeoPoint.of(-90.0, -180.0).isRight())
    }

    @Test
    fun equalCoordinates_areEqual() {
        assertEquals(GeoPoint.of(1.0, 2.0).getOrNull(), GeoPoint.of(1.0, 2.0).getOrNull())
    }
}
