package com.hopcape.odo.core.platform.notification

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * Reports notifications as enabled and opens Odo's page in Settings.
 *
 * The permission is not read: iOS answers that asynchronously and nothing on iOS posts a
 * notification yet (the MVP is Android-only). Claiming "enabled" keeps the screen quiet
 * rather than warning about a block that has not been checked.
 */
internal class IosSystemNotificationSettings : SystemNotificationSettings {

    override fun areEnabled(): Boolean = true

    override fun open() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}
