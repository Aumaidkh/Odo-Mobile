package com.hopcape.odo.core.platform.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Finds the manufacturer's autostart page, on the builds that have one.
 *
 * These component names are not API. They are the activities each skin happens to ship, they
 * differ between versions, and any of them can disappear in an update — which is why every one
 * is tried in turn and a total failure is reported rather than thrown. The owner is never left
 * on a button that silently did nothing: the screen says where to look when this returns false.
 */
internal class AndroidBackgroundStartAccess(
    private val context: Context,
) : BackgroundStartAccess {

    override fun needsAttention(): Boolean =
        Build.MANUFACTURER.lowercase() in RESTRICTIVE_MANUFACTURERS

    override fun open(): Boolean {
        val opened = CANDIDATES.any { (pkg, cls) ->
            val intent = Intent()
                .setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }.isSuccess
        }
        if (opened) return true

        // No skin-specific autostart page, which is the normal case on stock Android and on the
        // builds — Samsung's among them — that fold the same decision into battery optimisation
        // instead. This list is where those phones keep it. It only opens the list; it does not
        // ask for an exemption, so it needs no permission of its own.
        val battery = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(battery) }.isSuccess) return true

        // Neither resolved. The app's own settings page at least puts the owner one level from
        // the battery and autostart controls on every build that has them.
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    private companion object {
        /**
         * The skins that hold background starts behind a switch of their own.
         *
         * Not a complete list and cannot be: it is a judgement about which builds are worth
         * warning on, and the cost of being wrong is one dismissible line.
         */
        val RESTRICTIVE_MANUFACTURERS = setOf(
            "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo",
            "huawei", "honor", "meizu", "asus", "letv",
        )

        /** Package and activity of each skin's autostart screen, tried in order. */
        val CANDIDATES = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        )
    }
}
