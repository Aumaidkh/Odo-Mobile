package com.hopcape.odo.feature.advisory.domain.checklist

import com.hopcape.odo.core.domain.advisory.matching.BillLineMatcher
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.schedule.ServiceInterval
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItemDraft
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreServiceChecklistBuilderTest {

    private val builder = PreServiceChecklistBuilder(BillLineMatcher())

    @Test
    fun aCarWithNoRecordIsDueForEveryJobItHasDrivenPast() {
        val list = builder.build(
            schedule = mapOf(
                "engine_oil" to ServiceInterval("engine_oil", "Engine oil + filter", km = 10_000),
                "coolant" to ServiceInterval("coolant", "Coolant flush", km = 60_000),
            ),
            history = emptyList(),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        assertEquals(listOf("engine_oil"), list.due.map { it.slug })
        assertEquals(ChecklistReason.NeverRecorded, list.due.single().reason)
        assertEquals(ChecklistReason.KmToGo(18_000), list.notYet.first { it.slug == "coolant" }.reason)
    }

    @Test
    fun aJobDoneRecentlyIsNotDueAndSaysHowFarIsLeft() {
        val list = builder.build(
            schedule = mapOf("engine_oil" to ServiceInterval("engine_oil", "Engine oil + filter", km = 10_000)),
            history = listOf(entry(LocalDate(2026, 6, 1), "Engine oil", odometerKm = 38_000)),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        assertTrue(list.due.none { it.slug == "engine_oil" })
        assertEquals(ChecklistReason.KmToGo(6_000), list.notYet.first { it.slug == "engine_oil" }.reason)
    }

    @Test
    fun aJobDrivenPastItsIntervalSaysHowFarAgoItWasDone() {
        val list = builder.build(
            schedule = mapOf("engine_oil" to ServiceInterval("engine_oil", "Engine oil + filter", km = 10_000)),
            history = listOf(entry(LocalDate(2025, 6, 1), "Engine oil", odometerKm = 31_000)),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        assertEquals(ChecklistReason.LastDoneKmAgo(11_000), list.due.single { it.slug == "engine_oil" }.reason)
    }

    @Test
    fun aTimeOnlyJobIsDueOnMonthsAloneAndCountsFromTheDayTheCarWasAdded() {
        val schedule = mapOf("brake_fluid" to ServiceInterval("brake_fluid", "Brake fluid", months = 36))

        val neverDone = builder.build(
            schedule = schedule,
            history = emptyList(),
            currentKm = 42_000,
            carAddedOn = LocalDate(2023, 1, 1),
            today = TODAY,
        )
        assertEquals(ChecklistReason.NeverRecorded, neverDone.due.single().reason)

        val doneLastYear = builder.build(
            schedule = schedule,
            history = listOf(entry(LocalDate(2025, 9, 1), "Brake oil top up", odometerKm = 30_000)),
            currentKm = 42_000,
            carAddedOn = LocalDate(2023, 1, 1),
            today = TODAY,
        )
        // Whole months, rounded down: 5 Sep 2026 to 1 Sep 2028 is 23 months and change, and a
        // deadline is better understated than overstated.
        assertEquals(ChecklistReason.MonthsToGo(23), doneLastYear.notYet.single { it.slug == "brake_fluid" }.reason)
    }

    @Test
    fun servicesSinceCountsTheEntriesFiledAfterTheJobWasLastDone() {
        val list = builder.build(
            schedule = mapOf("air_filter" to ServiceInterval("air_filter", "Air filter", km = 20_000)),
            history = listOf(
                entry(LocalDate(2024, 1, 1), "Air filter", odometerKm = 15_000),
                entry(LocalDate(2025, 1, 1), "General service", odometerKm = 27_000),
                entry(LocalDate(2026, 1, 1), "General service", odometerKm = 38_000),
            ),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        assertEquals(2, list.due.single { it.slug == "air_filter" }.servicesSince)
    }

    @Test
    fun aRowWithBothFiguresQuotesTheNearerDeadlineRatherThanAlwaysTheDistance() {
        // 40,000 km or 24 months, done 1,000 km and 23 months ago. The distance figure is
        // true and useless: the owner has four weeks, not 39,000 km.
        val list = builder.build(
            schedule = mapOf("brake_fluid" to ServiceInterval("brake_fluid", "Brake fluid", km = 40_000, months = 24)),
            history = listOf(entry(LocalDate(2024, 10, 5), "Brake fluid", odometerKm = 41_000)),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        assertEquals(ChecklistReason.MonthsToGo(1), list.notYet.single { it.slug == "brake_fluid" }.reason)
    }

    @Test
    fun aScheduledJobIsNeverLabelledNotInTheSchedule() {
        // Months-only, and nothing to count from. "Not in the schedule" is the section the
        // owner is told to refuse, and the maker does ask for this one.
        val list = builder.build(
            schedule = mapOf("brake_fluid" to ServiceInterval("brake_fluid", "Brake fluid", months = 36)),
            history = emptyList(),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        assertEquals(ChecklistReason.NeverRecorded, list.due.single { it.slug == "brake_fluid" }.reason)
    }

    @Test
    fun aScheduleThatSaidNothingIsEmptyEvenThoughTheUpsellsAreAlwaysThere() {
        val list = builder.build(
            schedule = emptyMap(),
            history = emptyList(),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        assertTrue(list.isEmpty)
        assertEquals(3, list.notYet.size)
    }

    @Test
    fun theUpsellsAreListedAsNotInTheScheduleAndCappedAtThree() {
        val list = builder.build(
            schedule = emptyMap(),
            history = emptyList(),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        assertEquals(3, list.notYet.size)
        assertTrue(list.notYet.all { it.reason == ChecklistReason.NotInSchedule })
        assertTrue(list.notYet.all { it.label is ItemLabel.Upsell })
    }

    @Test
    fun anUpsellTheScheduleActuallyAsksForIsNotCalledUnscheduled() {
        val list = builder.build(
            schedule = mapOf(
                CounterUpsell.INJECTOR_CLEANING.slug to
                    ServiceInterval(CounterUpsell.INJECTOR_CLEANING.slug, "Injector cleaning", km = 60_000),
            ),
            history = emptyList(),
            currentKm = 42_000,
            carAddedOn = null,
            today = TODAY,
        )

        val injector = (list.due + list.notYet).single { it.slug == CounterUpsell.INJECTOR_CLEANING.slug }
        assertEquals(ItemLabel.FromSchedule("Injector cleaning"), injector.label)
    }

    private fun entry(on: LocalDate, vararg labels: String, odometerKm: Int) =
        ServiceLogEntry.create(
            id = ServiceLogId("entry-$on"),
            carId = CarId("car-1"),
            ownerId = OwnerId("owner-1"),
            serviceDate = on,
            odometerKm = odometerKm,
            totalAmountPaise = 100_000,
            today = on,
            lineItems = labels.map {
                ServiceLogLineItemDraft(label = it, category = ServiceCategory.OTHER, amountPaise = 50_000)
            },
        ).getOrNull()!!

    private companion object {
        val TODAY = LocalDate(2026, 9, 5)
    }
}
