package com.hopcape.odo.core.platform.app

import platform.UIKit.UIDevice

/**
 * Reads the model and OS version from `UIDevice`.
 *
 * `UIDevice.model` gives the product family — "iPhone", not "iPhone 15 Pro". The exact
 * model needs a `uname` lookup mapped through a table of identifiers that has to be updated
 * every year, which is more upkeep than a support mail footer is worth.
 */
internal class IosDeviceInfo : DeviceInfo {

    override val manufacturer: String = "Apple"

    override val model: String = UIDevice.currentDevice.model

    override val osVersion: String =
        "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"
}
