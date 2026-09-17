package com.hopcape.odo.infrastructure.supabase.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.device.AnalyticsInstallId
import com.hopcape.odo.core.domain.device.DeviceRegistry
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.app.DeviceInfo
import com.hopcape.odo.core.platform.app.InstallationId
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Writes this install into `user_devices` through the `record_device_seen` RPC.
 *
 * An RPC and not a table insert: the function stamps `owner_id` from the session, so the
 * device cannot be attached to an account the caller merely names.
 */
internal class SupabaseDeviceRegistry(
    private val postgrest: PostgrestClient,
    private val installationId: InstallationId,
    private val deviceInfo: DeviceInfo,
    private val appInfo: AppInfo,
    private val analyticsInstallId: AnalyticsInstallId,
    private val telemetry: SupabaseTelemetry,
    private val clock: Clock = Clock.System,
    private val minInterval: Duration = MIN_INTERVAL,
) : DeviceRegistry {

    /**
     * When this process last wrote a row. In memory on purpose: a process restart is itself
     * worth recording, so losing the guard is the behaviour we want.
     */
    private var lastRecordedAt: Instant? = null

    override suspend fun recordSeen(): Either<DomainError, Unit> {
        val now = clock.now()
        val last = lastRecordedAt
        if (last != null && now - last < minInterval) return Unit.right()

        // Null when the SDK has no id yet. The server keeps whatever it already has rather
        // than overwriting it with nothing, so an early call costs nothing.
        val appInstanceId = analyticsInstallId.value()
        if (appInstanceId == null) telemetry.deviceMissingAnalyticsId()

        return try {
            postgrest.rpc(
                function = FUNCTION,
                params = JsonObject(
                    mapOf(
                        PARAM_INSTALL_ID to JsonPrimitive(installationId.value),
                        PARAM_PLATFORM to JsonPrimitive(deviceInfo.platform),
                        PARAM_APP_INSTANCE_ID to (appInstanceId?.let(::JsonPrimitive) ?: JsonNull),
                        PARAM_APP_VERSION to JsonPrimitive(appInfo.versionName),
                        PARAM_OS_VERSION to JsonPrimitive(deviceInfo.osVersion),
                        PARAM_MODEL to JsonPrimitive("${deviceInfo.manufacturer} ${deviceInfo.model}"),
                    ),
                ),
                // The stamped time, which is only ever read to prove the row landed.
                serializer = String.serializer().nullable,
            )
            lastRecordedAt = now
            Unit.right()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // One case for every failure: nothing branches on why a heartbeat missed, and
            // PostgrestClient has already logged the status.
            DomainError.PersistenceFailure().left()
        }
    }

    private companion object {
        /** A foreground happens often; the row only has to be accurate to the hour. */
        val MIN_INTERVAL = 1.hours
        const val FUNCTION = "record_device_seen"
        const val PARAM_INSTALL_ID = "p_install_id"
        const val PARAM_PLATFORM = "p_platform"
        const val PARAM_APP_INSTANCE_ID = "p_app_instance_id"
        const val PARAM_APP_VERSION = "p_app_version"
        const val PARAM_OS_VERSION = "p_os_version"
        const val PARAM_MODEL = "p_model"
    }
}
