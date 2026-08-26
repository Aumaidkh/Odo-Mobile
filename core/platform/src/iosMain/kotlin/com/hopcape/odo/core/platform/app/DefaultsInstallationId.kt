package com.hopcape.odo.core.platform.app

import platform.Foundation.NSUserDefaults
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [InstallationId] on `NSUserDefaults` — the iOS mirror of `PrefsInstallationId`.
 *
 * `identifierForVendor` was considered and rejected: it changes when the last app from the
 * same vendor is removed and reinstalled, and it is empty while the device is locked before
 * first unlock. A value this app generates and stores is stable under both.
 */
internal class DefaultsInstallationId : InstallationId {

    private val defaults = NSUserDefaults.standardUserDefaults

    @OptIn(ExperimentalUuidApi::class)
    override val value: String by lazy {
        defaults.stringForKey(KEY_INSTALL_ID) ?: Uuid.random().toString().also {
            defaults.setObject(it, KEY_INSTALL_ID)
        }
    }

    private companion object {
        const val KEY_INSTALL_ID = "odo_install_id"
    }
}
