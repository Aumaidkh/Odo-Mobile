package com.hopcape.odo.infrastructure.database.sync

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.coreDataModule
import com.hopcape.odo.infrastructure.database.databaseInfrastructureModule
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
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
 * This guards a trap that fails silently. Registering each adapter as `single<Syncable> {
 * … }` looks right and compiles, but in Koin a second definition of the same type
 * *replaces* the first — so seven registrations would leave one, `getAll` would return
 * one, and six tables would simply never sync. Nothing errors; data just stops arriving.
 * The house pattern, `single { Concrete(…) } bind Syncable::class`, keeps the primary
 * types distinct so all seven survive.
 *
 * Runs against the real `coreDataModule` and `databaseInfrastructureModule`, with the
 * SQLDelight driver swapped for an in-memory one — which also means `DriverFactory` is
 * never resolved and the host JVM does not need an Android `Context`.
 *
 * Unlike before the local-data-source split, the `Syncable` for an entity and its
 * repository are two different objects — `CarSyncable` wraps the same `SyncRunner` the
 * repository used to hold directly. Both are still Koin singletons over the one shared
 * `OdoDatabase`, so there is no duplicated query cache to guard against; the "same
 * instance" assertion this file used to carry no longer describes a real invariant and is
 * gone.
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
    fun eachSyncableIsAStableSingleton() {
        val koin = graph()

        // Resolved twice, same object — a fresh instance per collection would mean a fresh
        // SyncRunner (and a fresh SyncTable) per sync pass instead of one shared cursor.
        val first = koin.getAll<Syncable>().first { it.entity == SyncEntity.CARS }
        val second = koin.getAll<Syncable>().first { it.entity == SyncEntity.CARS }
        assertSame(first, second)
    }

    @Test
    fun unbuiltEntitiesAreDeclaredButNotRegistered() {
        // bills and bill_line_items hold their positions in the push order so the
        // foreign-key ordering is already right when they are built — but nothing
        // registers them, and the engine simply does not see them.
        val entities = graph().getAll<Syncable>().map { it.entity }.toSet()

        assertEquals(emptySet(), entities intersect setOf(SyncEntity.BILLS, SyncEntity.BILL_LINE_ITEMS))
    }

    /**
     * `coreDataModule` + `databaseInfrastructureModule`, with the driver and the ports
     * they expect from elsewhere supplied.
     */
    private fun graph() = koinApplication {
        modules(
            databaseInfrastructureModule,
            coreDataModule,
            module {
                // Overrides databaseInfrastructureModule's `get<DriverFactory>().create()`,
                // so the factory — which needs an Android Context — is never resolved at all.
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
