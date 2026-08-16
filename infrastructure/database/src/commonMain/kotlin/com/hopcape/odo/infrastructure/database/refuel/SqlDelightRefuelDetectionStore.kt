package com.hopcape.odo.infrastructure.database.refuel

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.domain.refuel.DetectionApp
import com.hopcape.odo.core.domain.refuel.FuelMerchantClassifier
import com.hopcape.odo.core.domain.refuel.IgnoredMerchant
import com.hopcape.odo.core.domain.refuel.RefuelDetectionSettings
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Refuel_detection_settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [RefuelDetectionStore]. Entirely device-local — none of these tables
 * carry sync columns, and none of them should.
 *
 * The settings row is created on demand rather than seeded at install: a phone that never
 * opens the auto-detect screen has no row, and the absence of one reads as
 * [RefuelDetectionSettings.Default], which is detection off. Writing a row at install would
 * be recording a decision the owner has not made.
 */
internal class SqlDelightRefuelDetectionStore(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RefuelDetectionStore {

    private val queries get() = database.refuelDetectionQueries

    override fun observeSettings(): Flow<RefuelDetectionSettings> =
        queries.selectDetectionSettings()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() ?: RefuelDetectionSettings.Default }

    override suspend fun settings(): RefuelDetectionSettings =
        queries.selectDetectionSettings().executeAsOneOrNull()?.toDomain()
            ?: RefuelDetectionSettings.Default

    /**
     * Insert-then-update in one transaction, because minSdk 26 ships SQLite 3.18 and it has
     * no UPSERT. The insert is ignored when the row is already there, so the update is what
     * actually writes in the common case.
     */
    override suspend fun saveSettings(settings: RefuelDetectionSettings) {
        database.transaction {
            queries.insertDetectionSettingsIfAbsent(
                detectEnabled = settings.detectEnabled.toLong(),
                confirmBeforeLog = settings.confirmBeforeLog.toLong(),
                predictOdometer = settings.predictOdometer.toLong(),
                autostartAck = settings.autostartAcknowledged.toLong(),
            )
            queries.updateDetectionSettings(
                detectEnabled = settings.detectEnabled.toLong(),
                confirmBeforeLog = settings.confirmBeforeLog.toLong(),
                predictOdometer = settings.predictOdometer.toLong(),
                autostartAck = settings.autostartAcknowledged.toLong(),
            )
        }
    }

    override fun observeApps(): Flow<List<DetectionApp>> =
        queries.selectDetectionApps()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { DetectionApp(it.package_name, it.enabled == 1L) } }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
        database.transaction {
            queries.insertDetectionAppIfAbsent(packageName = packageName, enabled = enabled.toLong())
            queries.updateDetectionApp(packageName = packageName, enabled = enabled.toLong())
        }
    }

    /**
     * Only inserts. A package the owner has already turned off must stay off through every
     * refresh of the installed-app list, so this deliberately does not update.
     */
    override suspend fun registerApp(packageName: String, enabledByDefault: Boolean) {
        queries.insertDetectionAppIfAbsent(
            packageName = packageName,
            enabled = enabledByDefault.toLong(),
        )
    }

    override fun observeIgnoredMerchants(): Flow<List<IgnoredMerchant>> =
        queries.selectIgnoredMerchants()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { IgnoredMerchant(key = it.merchant_key, label = it.label) } }

    override suspend fun ignoredMerchantKeys(): Set<String> =
        queries.selectIgnoredMerchants().executeAsList().map { it.merchant_key }.toSet()

    /**
     * Stored under the classifier's own key, so the same merchant spelled differently by two
     * payment apps is one entry rather than two. The label keeps the name as the owner saw
     * it, because that is what the settings screen shows back to them.
     */
    override suspend fun ignoreMerchant(merchant: String) {
        queries.ignoreMerchant(
            merchantKey = FuelMerchantClassifier.keyFor(merchant),
            label = merchant.trim(),
            ignoredAt = clock.now().toString(),
        )
    }

    override suspend fun unignoreMerchant(merchantKey: String) {
        queries.unignoreMerchant(merchantKey)
    }

    private fun Refuel_detection_settings.toDomain() = RefuelDetectionSettings(
        detectEnabled = detect_enabled == 1L,
        confirmBeforeLog = confirm_before_log == 1L,
        predictOdometer = predict_odometer == 1L,
        autostartAcknowledged = autostart_ack == 1L,
    )

    private fun Boolean.toLong(): Long = if (this) 1L else 0L
}
