package com.hopcape.odo.core.data.sync

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.coreDataModule
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.performance.api.PerformanceTracer
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Every syncable repository must reach the engine through `getAll<Syncable>()`.
 *
 * This guards a trap that fails silently. Registering each repository as
 * `single<Syncable> { … }` looks right and compiles, but in Koin a second definition of the
 * same type *replaces* the first — so six registrations would leave one, `getAll` would
 * return one, and five tables would simply never sync. Nothing errors; data just stops
 * arriving. The house pattern, `single { Concrete(…) } bind Syncable::class`, keeps the
 * primary types distinct so all six survive.
 *
 * Runs against the real `coreDataModule`, with the SQLDelight driver swapped for an
 * in-memory one — which also means `DriverFactory` is never resolved and the host JVM does
 * not need an Android `Context`.
 */
class SyncableRegistrationTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun everyBuiltEntityIsCollectedByGetAll() {
        val koin = graph()

        val entities = koin.getAll<Syncable>().map { it.entity }.toSet()

        assertEquals(
            setOf(
                SyncEntity.PROFILES,
                SyncEntity.CARS,
                SyncEntity.SERVICE_LOGS,
                SyncEntity.OVERCHARGE_REPORTS,
                SyncEntity.DOCUMENTS,
                SyncEntity.HEALTH_SCORES,
                SyncEntity.REMINDERS,
            ),
            entities,
            "a missing entity here means that table silently stops syncing",
        )
    }

    @Test
    fun theSyncableAndTheRepositoryAreTheSameInstance() {
        val koin = graph()

        // Two bindings, one object. The sync half needs the same row↔DTO mapping the read
        // half has; two instances would mean two SQLDelight query caches over one table.
        val asSyncable: Any = koin.getAll<Syncable>().first { it.entity == SyncEntity.CARS }
        val asRepository: Any = koin.get<CarRepository>()
        assertSame(asSyncable, asRepository)
    }

    @Test
    fun unbuiltEntitiesAreDeclaredButNotRegistered() {
        // bills and bill_line_items hold their positions in the push order so the
        // foreign-key ordering is already right when they are built — but nothing
        // registers them, and the engine simply does not see them.
        val entities = graph().getAll<Syncable>().map { it.entity }.toSet()

        assertEquals(emptySet(), entities intersect setOf(SyncEntity.BILLS, SyncEntity.BILL_LINE_ITEMS))
    }

    /** `coreDataModule`, with the driver and the ports it expects from elsewhere supplied. */
    private fun graph() = koinApplication {
        modules(
            coreDataModule,
            module {
                // Overrides coreDataModule's `get<DriverFactory>().create()`, so the factory
                // — which needs an Android Context — is never resolved at all.
                single<SqlDriver> {
                    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(OdoDatabase.Schema::create)
                }
                single<Logger> { NoopLogger }
                single<PerformanceTracer> { NoopTracer }
                single<CrashRecorder> { NoopCrash }
                single { silentSyncTelemetry() }
                single<IdGenerator> { IdGenerator { "test-id" } }
                single<SessionStatusProvider> { SessionStatusProvider { false } }
                single<CurrentOwnerProvider> { CurrentOwnerProvider { OwnerId("owner-1") } }
                // The blob uploader reads files off the device, which is a platform binding.
                single<PlatformFileStore> { noopBlobUploaderFileStore }
            },
        )
    }.koin
}
