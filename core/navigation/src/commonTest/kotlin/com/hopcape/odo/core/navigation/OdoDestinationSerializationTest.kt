package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every destination survives being written to saved state and read back.
 *
 * This is what makes the back stack outlive a configuration change or process death. The
 * failure it guards against is not a compile error: a key that cannot be written is a crash,
 * or a silent reset to the start destination, at the moment an owner switches their phone to
 * dark mode — on whichever screen they happened to be on.
 *
 * The subtype list is read out of the serializer's own descriptor rather than written by hand,
 * so a destination added later is not quietly untested. Adding one without adding it to
 * [allDestinations] fails [everyDestinationIsCovered].
 */
class OdoDestinationSerializationTest {

    /**
     * One instance of every concrete destination, with arguments that are not defaults —
     * a round trip that drops a field would otherwise still pass.
     */
    private val allDestinations: List<OdoDestination> = listOf(
        OdoDestination.Home,
        OdoDestination.Welcome,
        OdoDestination.Onboarding,
        OdoDestination.CarDetail(carId = "car-1"),
        OdoDestination.Paywall(trigger = "SCAN_LIMIT", amountPaise = 4_999L, freeScans = 2),
        OdoDestination.FilePreview(
            storageKey = "scans/bill-1.jpg",
            title = "March service",
            remoteBucket = OdoDestination.FilePreview.BUCKET_BILL_PHOTOS,
        ),
        OdoDestination.Fairness(
            items = listOf(FairnessLineInput(label = "Oil change", category = "OIL", amountPaise = 120_000L)),
            logId = "log-1",
            carId = "car-1",
        ),
        // Profile
        OdoDestination.Profile.Root,
        OdoDestination.Profile.Edit,
        OdoDestination.Profile.Notifications,
        OdoDestination.Profile.Privacy,
        OdoDestination.Profile.DeleteAccount,
        OdoDestination.Profile.Units,
        OdoDestination.Profile.Appearance,
        OdoDestination.Profile.Export,
        OdoDestination.Profile.SignOut,
        // Garage — Home is both a Garage key and a TopLevel one, which is the case most
        // likely to confuse a sealed hierarchy that can reach a subtype by two paths.
        OdoDestination.Garage.Home,
        OdoDestination.Garage.CarActions,
        OdoDestination.Garage.UpdateOdometer,
        OdoDestination.Garage.AddToHistory,
        OdoDestination.Garage.Export,
        OdoDestination.Garage.RemoveCar,
        OdoDestination.Garage.EditCar,
        OdoDestination.Garage.AddCar,
        // Reminders
        OdoDestination.Reminders.List,
        OdoDestination.Reminders.Settings,
        OdoDestination.Reminders.New(reminderId = "rem-1"),
        OdoDestination.Reminders.Actions(
            kind = "INSURANCE",
            dueOn = "2026-04-01",
            customId = "custom-1",
            title = "Insurance renewal",
            due = "in 12 days",
            icon = "SHIELD",
        ),
        // Auto-odometer
        OdoDestination.AutoOdometer.Education(
            mode = OdoDestination.AutoOdometer.AutoOdometerFlowMode.NO_STEREO,
        ),
        OdoDestination.AutoOdometer.NotificationRationale(
            mode = OdoDestination.AutoOdometer.AutoOdometerFlowMode.NO_STEREO,
        ),
        OdoDestination.AutoOdometer.DevicePicker,
        OdoDestination.AutoOdometer.PermissionSetup(
            mode = OdoDestination.AutoOdometer.AutoOdometerFlowMode.NO_STEREO,
        ),
        OdoDestination.AutoOdometer.TripLogged(tripId = "trip-1"),
        OdoDestination.AutoOdometer.Settings,
        // Service log
        OdoDestination.ServiceLog.List(carId = "car-1"),
        OdoDestination.ServiceLog.Detail(logId = "log-1", carId = "car-1"),
        OdoDestination.ServiceLog.AddEdit(carId = "car-1", editLogId = "log-1"),
        OdoDestination.ServiceLog.ReportOvercharge(logId = "log-1", carId = "car-1"),
        OdoDestination.ServiceLog.Share(carId = "car-1", logId = "log-1"),
        // Bill scanner
        OdoDestination.BillScanner.Capture(target = ScanTarget.Document, documentType = "INSURANCE"),
        OdoDestination.BillScanner.Review(photoKey = "scans/bill-1.jpg"),
        OdoDestination.BillScanner.DocumentReview(
            photoKey = "scans/rc-1.jpg",
            documentType = "RC",
            origin = DocumentOrigin.Uploaded,
        ),
        OdoDestination.BillScanner.SaveSuccess,
        OdoDestination.BillScanner.ReportSuccess,
        OdoDestination.BillScanner.ScanError,
        // Refuel
        OdoDestination.Refuel.Log,
        OdoDestination.Refuel.Confirm(
            draft = FuelFillDraftInput(
                source = "DETECTED",
                amountPaise = 200_000,
                amountOrigin = "CAPTURED",
                stationName = "Bharat Petroleum",
                transactionRef = "txn-1",
            ),
        ),
        OdoDestination.Refuel.Logged(fillId = "fill-1"),
        OdoDestination.Refuel.Pending,
        OdoDestination.Refuel.AutoDetect,
        // Cost tracker · Timeline · Health score
        OdoDestination.CostTracker.Home,
        OdoDestination.CostTracker.FuelRate,
        OdoDestination.Timeline.List,
        OdoDestination.Timeline.Filter,
        OdoDestination.HealthScore.Detail,
        OdoDestination.HealthScore.Info,
        // Documents
        OdoDestination.Documents.Vault,
        OdoDestination.Documents.Add(prefillType = "PUC"),
        OdoDestination.Documents.Detail(documentId = "doc-1"),
        OdoDestination.Documents.AddSuccess(documentId = "doc-1"),
        OdoDestination.Documents.Share(documentId = "doc-1"),
        OdoDestination.Documents.EditDates(documentId = "doc-1"),
        // Support
        OdoDestination.Support.Help,
        OdoDestination.Support.Search,
        OdoDestination.Support.Chat,
        OdoDestination.Support.Email,
        OdoDestination.Support.Tickets,
        OdoDestination.Support.ReportProblem,
        OdoDestination.Support.SuggestIdea,
        OdoDestination.Support.FlagPriceData,
        OdoDestination.Support.Rate,
        OdoDestination.Support.Faqs,
        OdoDestination.Support.Terms,
        OdoDestination.Support.Privacy,
        OdoDestination.Support.Licences,
        // Auth — `next` is itself a destination, so these also cover a key nested in a key.
        OdoDestination.Auth.Phone(next = OdoDestination.Garage.Home),
        OdoDestination.Auth.Otp(phone = "9876543210", next = OdoDestination.Timeline.List),
        OdoDestination.Auth.Verifying(next = OdoDestination.CostTracker.Home),
    )

    @Test
    fun everyDestinationSurvivesARoundTrip() {
        allDestinations.forEach { destination ->
            val encoded = json.encodeToString(OdoDestination.serializer(), destination)
            assertEquals(
                destination,
                json.decodeFromString(OdoDestination.serializer(), encoded),
                "$destination did not come back as itself",
            )
        }
    }

    @Test
    fun everyDestinationIsCovered() {
        // Read from the serializer rather than written down, so this fails when a destination
        // is added without a case above instead of passing on a stale list.
        assertEquals(
            OdoDestination.serializer().descriptor.concreteSerialNames(),
            allDestinations.map(::serialNameOf).toSet(),
        )
    }

    @Test
    fun aWholeBackStackSurvivesARoundTrip() {
        // What actually gets saved: the stack, not one key. A deep stack rather than a pair,
        // because restoring the top alone would still leave the owner unable to go back.
        val serializer = NavBackStackSerializer(OdoDestination.serializer())
        val stack = NavBackStack<OdoDestination>(
            OdoDestination.Home,
            OdoDestination.Timeline.List,
            OdoDestination.ServiceLog.Detail(logId = "log-1", carId = "car-1"),
            OdoDestination.FilePreview(storageKey = "scans/bill-1.jpg"),
        )

        val restored = json.decodeFromString(serializer, json.encodeToString(serializer, stack))

        assertEquals(stack.toList(), restored.toList())
    }

    private val json = Json

    /** The type discriminator a destination is written under. */
    private fun serialNameOf(destination: OdoDestination): String =
        json.encodeToJsonElement(OdoDestination.serializer(), destination)
            .jsonObject
            .getValue(TYPE_KEY)
            .jsonPrimitive
            .content

    /**
     * Every concrete destination the sealed hierarchy can encode, by serial name.
     *
     * A sealed descriptor holds its subtypes under its second element; a subtype that is
     * itself sealed (this registry nests them per feature) holds its own the same way, so
     * this walks down to the leaves.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun SerialDescriptor.concreteSerialNames(): Set<String> =
        if (kind == PolymorphicKind.SEALED) {
            getElementDescriptor(1).elementDescriptors.flatMap { it.concreteSerialNames() }.toSet()
        } else {
            setOf(serialName)
        }

    private companion object {
        /** kotlinx-serialization's default discriminator for a polymorphic value. */
        const val TYPE_KEY = "type"
    }
}
