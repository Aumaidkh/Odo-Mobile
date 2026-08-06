package com.hopcape.odo.feature.reminders

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.reminders.domain.notification.ReminderNotificationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.time.Instant

internal val TEST_CAR = CarId("car-1")
internal val TEST_OWNER = OwnerId("owner-1")

/** 6 Aug 2026 — the day the reminder tests resolve their feed against. */
internal val TEST_TODAY = LocalDate(2026, 8, 6)
internal val TEST_CLOCK = FixedClock(Instant.parse("2026-08-06T09:00:00Z"))

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** Hands out one known id, so a test can predict a new reminder's identity. */
internal class FixedIdGenerator(private val id: String = "rem-new") : IdGenerator {
    override fun newId(): String = id
}

/** A stored custom reminder, built the way the data layer will rehydrate one. */
internal fun customReminder(
    id: String = "rem-1",
    title: String = "Air pressure check",
    cadence: ReminderCadence = ReminderCadence.EveryDays(15),
    startsOn: LocalDate = TEST_TODAY,
    preset: ReminderPreset? = null,
    anchorKm: Int? = null,
    paused: Boolean = false,
): CustomReminder = CustomReminder.reconstitute(
    id = ReminderId(id),
    ownerId = TEST_OWNER,
    carId = TEST_CAR,
    title = title,
    cadence = cadence,
    startsOn = startsOn,
    at = LocalTime(9, 0),
    paused = paused,
    addedOn = startsOn,
    preset = preset,
    anchorKm = anchorKm,
)

/** A stored document, built the way the data layer will rehydrate one. */
internal fun document(
    id: String = "doc-1",
    type: DocumentType = DocumentType.INSURANCE,
    expiresOn: LocalDate? = null,
): Document = Document.reconstitute(
    id = DocumentId(id),
    ownerId = TEST_OWNER,
    carId = TEST_CAR,
    type = type,
    storagePath = "documents/${TEST_CAR.value}/$id.pdf",
    source = DocumentSource.UPLOADED,
    addedOn = LocalDate(2026, 1, 1),
    expiresOn = expiresOn,
)

/**
 * In-memory [ReminderRepository], backed by [MutableStateFlow]s so readers re-emit
 * after a write. That matches how the real SQLDelight repository behaves.
 */
internal class FakeReminderRepository(
    initial: List<CustomReminder> = emptyList(),
    dismissals: List<ReminderDismissal> = emptyList(),
    private val failWith: DomainError? = null,
) : ReminderRepository {

    private val stored = MutableStateFlow(initial)
    private val dismissed = MutableStateFlow(dismissals)

    val customs: List<CustomReminder> get() = stored.value
    val recordedDismissals: List<ReminderDismissal> get() = dismissed.value

    override fun observeCustom(carId: CarId): Flow<List<CustomReminder>> =
        stored.map { all -> all.filter { it.carId == carId } }

    override fun observe(id: ReminderId): Flow<CustomReminder?> =
        stored.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun add(reminder: CustomReminder): Either<DomainError, CustomReminder> {
        failWith?.let { return it.left() }
        stored.value = stored.value + reminder
        return reminder.right()
    }

    override suspend fun update(reminder: CustomReminder): Either<DomainError, CustomReminder> {
        failWith?.let { return it.left() }
        stored.value = stored.value.map { if (it.id == reminder.id) reminder else it }
        return reminder.right()
    }

    override suspend fun softDelete(id: ReminderId): Either<DomainError, Unit> {
        failWith?.let { return it.left() }
        stored.value = stored.value.filterNot { it.id == id }
        return Unit.right()
    }

    override fun observeDismissals(carId: CarId): Flow<List<ReminderDismissal>> = dismissed

    override suspend fun dismiss(
        carId: CarId,
        dismissal: ReminderDismissal,
    ): Either<DomainError, Unit> {
        failWith?.let { return it.left() }
        dismissed.value = dismissed.value + dismissal
        return Unit.right()
    }
}

/** In-memory [DocumentRepository] — only what the feed use case reads. */
internal class FakeDocumentRepository(
    initial: List<Document> = emptyList(),
) : DocumentRepository {

    private val stored = MutableStateFlow(initial)

    override fun observe(carId: CarId): Flow<List<Document>> =
        stored.map { all -> all.filter { it.carId == carId } }

    override fun observe(id: DocumentId): Flow<Document?> =
        stored.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun add(document: Document): Either<DomainError, Document> = document.right()

    override suspend fun update(document: Document): Either<DomainError, Document> = document.right()

    override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> = Unit.right()

    override suspend fun countForOwner(ownerId: OwnerId): Int = stored.value.size
}

/** In-memory [ServiceLogRepository] — only what the feed use case reads. */
internal class FakeServiceLogRepository(
    entries: List<ServiceLogEntry> = emptyList(),
    readings: List<OdometerReading> = emptyList(),
) : ServiceLogRepository {

    private val storedEntries = MutableStateFlow(entries)
    private val storedReadings = MutableStateFlow(readings)

    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = storedEntries

    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> =
        storedEntries.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> =
        entry.right()

    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> =
        entry.right()

    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()

    override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? =
        storedReadings.value

    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = storedReadings
}

internal fun reading(km: Int, date: LocalDate = TEST_TODAY): OdometerReading = OdometerReading(
    logId = null,
    date = date,
    odometer = Distance.of(km).getOrNull()!!,
)

/** In-memory [AppSettingsRepository]; a write failure is opt-in via [failWith]. */
internal class FakeAppSettingsRepository(
    initial: AppSettings = AppSettings.Default,
    private val failWith: DomainError? = null,
) : AppSettingsRepository {

    private val stored = MutableStateFlow(initial)

    val settings: AppSettings get() = stored.value

    override fun observe(): Flow<AppSettings> = stored

    override suspend fun save(settings: AppSettings): Either<DomainError, AppSettings> {
        failWith?.let { return it.left() }
        stored.value = settings
        return settings.right()
    }
}

/** Records every schedule and cancel, so a test can check the write drove the right one. */
internal class RecordingScheduler : ReminderNotificationScheduler {
    val scheduled = mutableListOf<CustomReminder>()
    val cancelled = mutableListOf<ReminderId>()

    override suspend fun schedule(reminder: CustomReminder) {
        scheduled += reminder
    }

    override suspend fun cancel(id: ReminderId) {
        cancelled += id
    }
}
