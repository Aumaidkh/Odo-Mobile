package com.hopcape.odo.core.domain.refuel

import kotlinx.coroutines.flow.Flow

/**
 * Port for everything the payment-notification listener is allowed to do, and everything it
 * has learned.
 *
 * All of it is device-local. Notification access is granted to one phone, the packages it can
 * read are that phone's installed apps, and a merchant the owner rejected was rejected
 * against what that phone showed them. None of it mirrors a server table, and none of it
 * should follow the owner to another device where they have granted nothing.
 *
 * One port rather than three because the three are always read together: the worker that
 * receives a notice needs to know whether detection is on, whether that package is allowed,
 * and whether that merchant was rejected, before it can do anything at all.
 */
interface RefuelDetectionStore {

    /** The owner's switches, emitting again whenever any of them changes. */
    fun observeSettings(): Flow<RefuelDetectionSettings>

    /** The switches as they stand — for the worker, which has no screen to collect from. */
    suspend fun settings(): RefuelDetectionSettings

    suspend fun saveSettings(settings: RefuelDetectionSettings)

    /** Which payment apps may be read, keyed by package name. */
    fun observeApps(): Flow<List<DetectionApp>>

    suspend fun setAppEnabled(packageName: String, enabled: Boolean)

    /**
     * Record that a package can be offered to the owner, without changing a decision they
     * have already made about it.
     *
     * Called when the app list is refreshed against what is installed. A package the owner
     * turned off must stay off through every refresh, which is why this cannot be a plain
     * write.
     */
    suspend fun registerApp(packageName: String, enabledByDefault: Boolean)

    /** Merchants the owner said were not fuel. */
    fun observeIgnoredMerchants(): Flow<List<IgnoredMerchant>>

    suspend fun ignoredMerchantKeys(): Set<String>

    suspend fun ignoreMerchant(merchant: String)

    suspend fun unignoreMerchant(merchantKey: String)
}

/**
 * The three switches on the auto-detect screen.
 *
 * [confirmBeforeLog] defaults on. Silent logging is offered because an owner who trusts the
 * detection should not have to tap twice for every tank, but writing records the owner has
 * never seen is not a thing to do by default.
 */
data class RefuelDetectionSettings(
    val detectEnabled: Boolean = false,
    val confirmBeforeLog: Boolean = true,
    val predictOdometer: Boolean = true,
    /**
     * Whether the owner has said they have dealt with this phone's autostart setting.
     *
     * Their word, because there is nothing else to go on: no API reports whether a
     * manufacturer's own background-start switch is on. Without this the advice would sit on
     * the screen forever on those builds, including for the owner who followed it — which is
     * how a useful warning becomes furniture nobody reads.
     */
    val autostartAcknowledged: Boolean = false,
) {
    companion object {
        /** What a phone that has never opened the screen behaves like. */
        val Default = RefuelDetectionSettings()
    }
}

/** A payment app the listener can be pointed at. */
data class DetectionApp(
    val packageName: String,
    val enabled: Boolean,
)

/** A merchant the owner rejected, and the name they saw when they did. */
data class IgnoredMerchant(
    val key: String,
    val label: String,
)
