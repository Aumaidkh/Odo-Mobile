package com.hopcape.odo.core.platform.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Reads whether notification access has been granted, and opens the page where it is granted.
 *
 * The grant is checked against the system's own list of enabled listeners rather than by
 * asking whether the service is running. A service can be alive without being enabled, and a
 * screen that reported "on" in that state would be telling the owner detection works when
 * nothing would ever reach it.
 *
 * The check matches on the package rather than the exact component so that renaming the
 * service class later does not silently report a revoked permission on every phone that
 * granted the old one.
 */
internal class AndroidNotificationAccess(
    private val context: Context,
) : NotificationAccess {

    override fun isGranted(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)
        if (enabled.isNullOrEmpty()) return false
        return enabled.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it.packageName == context.packageName }
    }

    /**
     * Asks the package manager whether the component exists in this build's manifest.
     *
     * `getServiceInfo` throws `NameNotFoundException` for a component that is not declared,
     * which is the only way to tell — an undeclared service is not "disabled", it simply is
     * not there.
     */
    override fun isListenerDeclared(): Boolean = runCatching {
        context.packageManager.getServiceInfo(
            ComponentName(context, RefuelNotificationListenerService::class.java),
            0,
        )
    }.isSuccess

    /**
     * `FLAG_ACTIVITY_NEW_TASK` because the Context here is the application's, not an
     * activity's. A device with no such settings activity — rare, but possible on heavily
     * modified builds — leaves the owner where they are rather than crashing.
     */
    override fun open() {
        // From Android 11 the system can open *this app's own* notification-access page. The
        // list it falls back to is every listener-capable app on the phone, which on a loaded
        // device is a screen the owner has to search — and a permission they cannot find is a
        // permission they do not grant.
        val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(
                        context,
                        RefuelNotificationListenerService::class.java,
                    ).flattenToString(),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Not every build ships the detail page even on a new enough API, so a failure to
        // start it drops to the list rather than leaving the owner on a dead button.
        val opened = direct != null && runCatching { context.startActivity(direct) }.isSuccess
        if (!opened) runCatching { context.startActivity(fallback) }
    }

    /**
     * Ask the system for the binding back, without disturbing one that already works.
     *
     * Only the plain request. It is idempotent and cannot break anything: a bound listener
     * stays bound. The heavier repair below is deliberately *not* done here, because this runs
     * on every launch and the repair has a cost that only pays when something is actually
     * wrong.
     */
    override fun requestRebind() {
        if (!isGranted()) return
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, RefuelNotificationListenerService::class.java),
            )
        }
    }

    /**
     * The heavier fix, for a listener that is granted but will not bind.
     *
     * Rewriting the component's enabled state is what makes the system re-evaluate — a
     * package-component change is an event it reacts to, where a bare `requestRebind` after an
     * app update is one it can ignore. It is what recovers a reinstall on builds that would
     * otherwise never bind again.
     *
     * **It is destructive, which is why it is not on the launch path.** Disabling the component
     * tears down any binding that does exist, and if the process dies between the two writes
     * the component is left disabled and detection is off until something enables it again. So
     * it runs only when the listener is known not to be connected, and the enable is attempted
     * on its own even if the disable threw — leaving it disabled is the one outcome worth
     * guarding against.
     */
    override fun repairBinding() {
        if (!isGranted()) return
        val component = ComponentName(context, RefuelNotificationListenerService::class.java)
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        runCatching { NotificationListenerService.requestRebind(component) }
    }

    override fun isBound(): Boolean = PaymentNotices.isConnected()

    /**
     * `requestUnbind` needs the running service instance, which only exists while it is bound —
     * so the service does it, and this asks it to.
     */
    override fun releaseBinding() {
        PaymentNotices.requestUnbind()
    }

    /**
     * A periodic job whose only work is to ask for the binding back.
     *
     * Fifteen minutes is WorkManager's floor, not a tuned number — the job is a few
     * microseconds of work, and the interval only bounds how long detection can stay dead
     * before something notices. KEEP, so calling this on every launch does not restart the
     * clock and leave the job perpetually fifteen minutes away.
     */
    override fun keepBound() {
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                ListenerRearmWorker.TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ListenerRearmWorker>(
                    REARM_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                ).addTag(ListenerRearmWorker.TAG).build(),
            )
        }
    }

    private companion object {
        const val ENABLED_LISTENERS = "enabled_notification_listeners"

        /** WorkManager's minimum periodic interval. */
        const val REARM_INTERVAL_MINUTES = 15L
    }
}
