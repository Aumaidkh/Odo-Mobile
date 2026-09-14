package com.hopcape.odo.web.admin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogueDocumentTest {

    private val oil = ServiceItem(
        id = "a-dev-uuid",
        slug = "engine-oil-change",
        name = "Engine oil change",
        intervalKm = 10_000,
        intervalMonths = 12,
        benchmarkPaise = 280_000,
        notes = "Synthetic on newer cars.",
        isActive = true,
        appliesTo = listOf("petrol", "diesel"),
    )

    private val retired = ServiceItem(
        id = "another-dev-uuid",
        slug = "carburettor-tuning",
        name = "Carburettor tuning",
        intervalKm = null,
        intervalMonths = null,
        benchmarkPaise = null,
        notes = null,
        isActive = false,
    )

    private val tata = VehicleMake("make-tata", "Tata", 0)
    private val nexonBase = VehicleModel("model-tata-nexon", "make-tata", "Nexon", null, 0)
    private val nexonXz = VehicleModel("model-tata-nexon-xz", "make-tata", "Nexon", "XZ", 1)

    @Test
    fun `a service item survives the round trip`() {
        val read = CatalogueDocument.readServiceItems(CatalogueDocument.writeServiceItems(listOf(oil)))
        val back = (read as CatalogueDocument.Read.Rows).value.single()

        assertEquals(oil.slug, back.slug)
        assertEquals(oil.name, back.name)
        assertEquals(oil.intervalKm, back.intervalKm)
        assertEquals(oil.intervalMonths, back.intervalMonths)
        assertEquals(oil.benchmarkPaise, back.benchmarkPaise)
        assertEquals(oil.notes, back.notes)
        assertEquals(oil.appliesTo, back.appliesTo)
    }

    @Test
    fun `a retired item is still retired after the round trip`() {
        val read = CatalogueDocument.readServiceItems(CatalogueDocument.writeServiceItems(listOf(retired)))
        assertEquals(false, (read as CatalogueDocument.Read.Rows).value.single().isActive)
    }

    /** The id belongs to the project the file came from. Carrying it would plant it here. */
    @Test
    fun `a service item arrives without the id it was exported with`() {
        val read = CatalogueDocument.readServiceItems(CatalogueDocument.writeServiceItems(listOf(oil)))
        assertEquals("", (read as CatalogueDocument.Read.Rows).value.single().id)
    }

    @Test
    fun `a vehicle catalogue survives the round trip, ids and all`() {
        val document = CatalogueDocument.writeVehicles(listOf(tata), listOf(nexonBase, nexonXz))
        val back = (CatalogueDocument.readVehicles(document) as CatalogueDocument.Read.Rows).value

        assertEquals(listOf(tata), back.makes)
        assertEquals(listOf(nexonBase, nexonXz), back.models)
    }

    /** The trim-less row is what an owner who does not know their trim picks. */
    @Test
    fun `a null trim stays null`() {
        val document = CatalogueDocument.writeVehicles(listOf(tata), listOf(nexonBase))
        val back = (CatalogueDocument.readVehicles(document) as CatalogueDocument.Read.Rows).value
        assertEquals(null, back.models.single().variant)
    }

    @Test
    fun `a vehicle file is refused by the service catalogue`() {
        val vehicles = CatalogueDocument.writeVehicles(listOf(tata), listOf(nexonBase))
        assertEquals(CatalogueDocument.Read.WrongFile, CatalogueDocument.readServiceItems(vehicles))
    }

    @Test
    fun `a service file is refused by the vehicle catalogue`() {
        val services = CatalogueDocument.writeServiceItems(listOf(oil))
        assertEquals(CatalogueDocument.Read.WrongFile, CatalogueDocument.readVehicles(services))
    }

    @Test
    fun `something that is not JSON is refused rather than thrown`() {
        assertEquals(CatalogueDocument.Read.WrongFile, CatalogueDocument.readServiceItems("not a file"))
        assertEquals(CatalogueDocument.Read.WrongFile, CatalogueDocument.readVehicles(""))
    }

    /** A file from a later version imports what this version understands. */
    @Test
    fun `an unknown field is ignored`() {
        val document = """
            {
              "kind": "${CatalogueDocument.SERVICE_KIND}",
              "version": 1,
              "somethingNewer": true,
              "items": [{ "slug": "engine-oil-change", "display_name": "Engine oil change", "vat": 18 }]
            }
        """.trimIndent()

        val read = CatalogueDocument.readServiceItems(document)
        assertEquals("engine-oil-change", (read as CatalogueDocument.Read.Rows).value.single().slug)
    }

    @Test
    fun `an empty catalogue is a file, not a failure`() {
        val read = CatalogueDocument.readServiceItems(CatalogueDocument.writeServiceItems(emptyList()))
        assertTrue((read as CatalogueDocument.Read.Rows).value.isEmpty())
    }
}
