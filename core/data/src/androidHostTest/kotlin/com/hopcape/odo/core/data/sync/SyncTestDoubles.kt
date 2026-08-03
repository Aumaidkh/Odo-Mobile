package com.hopcape.odo.core.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import arrow.core.left
import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import com.hopcape.performance.api.Span

/**
 * Shared scaffolding for the sync tests: an in-memory database and silent observability.
 *
 * The repository tests each carry their own copy of these doubles. This file exists rather
 * than a seventh copy — the sync tests span several files and all need the same three.
 */
internal fun inMemoryDatabase(): Pair<OdoDatabase, JdbcSqliteDriver> {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    OdoDatabase.Schema.create(driver)
    return OdoDatabase(driver) to driver
}

internal fun silentDataTelemetry() =
    DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash)

internal fun silentSyncTelemetry() =
    SyncTelemetry(logger = NoopLogger, analytics = NoopAnalytics, tracer = NoopTracer, crash = NoopCrash)

internal object NoopAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) = Unit
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}

internal object NoopLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) = Unit

    override fun flush() = Unit
}

internal object NoopTracer : PerformanceTracer {
    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
        object : Span {
            override val spanId = "span"
            override val traceId = traceId
            override val parentSpanId = parentSpanId
            override val name = name
            override fun setAttribute(key: String, value: Any?): Span = this
        }

    override fun endSpan(span: Span) = Unit
    override fun flush() = Unit
}

internal object NoopCrash : CrashRecorder {
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}

/**
 * A [BlobUploader] over a device with no files and a server that refuses.
 *
 * The repository tests are about rows. An upload that answers "nothing here" leaves every
 * path untouched, which is exactly the behaviour those tests assume.
 */
internal fun noopBlobUploader() = BlobUploader(
    files = noopBlobUploaderFileStore,
    storage = noopRemoteFileStorage,
    telemetry = silentDataTelemetry(),
)

/** A device with no readable files. */
internal val noopBlobUploaderFileStore: PlatformFileStore = object : PlatformFileStore {
    override suspend fun save(pickedRef: String, directory: String, fileName: String) =
        DomainError.PersistenceFailure("no files in tests").left()

    override suspend fun delete(storageKey: String) = Unit
    override suspend fun exists(storageKey: String) = false
    override suspend fun bytes(storageKey: String) =
        DomainError.PersistenceFailure("no files in tests").left()
}

/** A bucket that refuses everything. */
private val noopRemoteFileStorage: RemoteFileStorage = object : RemoteFileStorage {
    override suspend fun upload(bucket: RemoteBucket, path: String, bytes: ByteArray, contentType: String) =
        DomainError.PersistenceFailure("no storage in tests").left()

    override suspend fun download(bucket: RemoteBucket, path: String) =
        DomainError.PersistenceFailure("no storage in tests").left()

    override suspend fun signedUrl(bucket: RemoteBucket, path: String, ttlSeconds: Long) =
        DomainError.PersistenceFailure("no storage in tests").left()

    override suspend fun remove(bucket: RemoteBucket, paths: List<String>) = Unit
}
