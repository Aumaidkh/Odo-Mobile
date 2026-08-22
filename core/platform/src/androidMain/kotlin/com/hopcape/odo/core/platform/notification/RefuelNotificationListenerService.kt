package com.hopcape.odo.core.platform.notification

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.domain.refuel.PaymentNotice
import com.hopcape.odo.core.domain.shared.Amount
import kotlin.time.Instant

/**
 * Reads payment notifications and forwards the two facts a fill needs.
 *
 * **What leaves this class is a merchant name, a sum and a timestamp.** Nothing is stored,
 * nothing is sent anywhere, and a notification from any package other than the ones the owner
 * enabled is dropped before its text is looked at. That last part is the point: the system
 * grants this service every notification on the phone, and the only thing standing between
 * that and the owner's messages is the filter at the top of [onNotificationPosted].
 *
 * The service is **not declared in the manifest** while
 * `<service>` entry is absent from the manifest, so the OS never binds it. This class
 * compiles and is tested either way; declaring it is what actually asks for the permission.
 *
 * It holds no dependencies and does no domain work. Deciding whether a payment was fuel needs
 * the owner's history and their settings, which is a suspending read this callback has no
 * business doing — so it publishes to [PaymentNotices] and returns, and the collector on the
 * other side does the thinking.
 */
class RefuelNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        forward(sbn ?: return)
    }

    /**
     * Read one notification and publish it, if it is one this service is allowed to read.
     *
     * Shared by the live callback and the catch-up sweep, so a payment that arrives while
     * bound and one found in the shade afterwards go through exactly the same filter — the
     * allow-list, then the text.
     */
    private fun forward(posted: StatusBarNotification) {
        // The allow-list first, before any text is read. A package the owner never enabled is
        // one whose contents this service has no reason to look at.
        if (!PaymentNotices.isWatched(posted.packageName)) {
            trace("not_watched", posted.packageName)
            return
        }

        val extras = posted.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val body = "$title $text".trim()
        if (body.isBlank()) {
            trace("no_text", posted.packageName)
            return
        }

        val merchant = PaymentNoticeReader.merchantIn(body)
        if (merchant == null) {
            trace("no_merchant", posted.packageName)
            return
        }
        trace("published", posted.packageName)
        PaymentNotices.publish(
            PaymentNotice(
                sourcePackage = posted.packageName,
                merchant = merchant,
                amount = PaymentNoticeReader.amountIn(body)?.let { Amount.of(it).getOrNull() },
                postedAt = Instant.fromEpochMilliseconds(posted.postTime),
            ),
        )
    }

    /**
     * Why a notification did or did not get past this service, without its contents.
     *
     * This feature is silent by design — every drop is a `return` with no trace — which is
     * correct behaviour and miserable to debug: "no notification appeared" has half a dozen
     * causes that look identical from outside. The reason is worth recording; the text is not.
     *
     * The package name is the one identifying detail here, and it is the one that matters:
     * `not_watched` is only actionable if you know which package was refused. No title, no
     * body, no merchant and no amount — a log line must never become the contents of somebody's
     * notification shade.
     */
    private fun trace(outcome: String, packageName: String) {
        HLogger.tag(TAG).d("notice_$outcome", mapOf("package" to packageName))
    }

    /**
     * Ignored on purpose.
     *
     * A payment notification being swiped away says nothing about the payment. The fill was
     * either detected when it arrived or it was not.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    /**
     * Catch up on whatever is already in the shade.
     *
     * The listener is bound long after some payments arrive — it was unbound when the phone
     * reclaimed the process, or the owner only granted access today — and everything posted in
     * the meantime was never seen. Anything still on screen can be read now, which recovers
     * exactly the payments the owner has not dealt with yet.
     *
     * A payment already handled is not asked about twice: the same notification produces the
     * same key every time, and the pending-fill store ignores a key it already holds.
     *
     * A notification the owner dismissed is gone, and no API can bring it back. That case is
     * the reason detections are written down when they happen rather than re-read later.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        HLogger.tag(TAG).i("listener_connected")
        PaymentNotices.markConnected(true)
        PaymentNotices.onUnbindRequested { runCatching { requestUnbind() } }
        val posted = runCatching { activeNotifications }.getOrNull() ?: return
        posted.forEach { sbn -> runCatching { forward(sbn) } }
    }

    /**
     * Ask for the connection back the moment it is lost.
     *
     * This is the hook that keeps detection alive without the owner ever opening Odo. The
     * system drops a listener for reasons that have nothing to do with the app — memory
     * pressure, a package change, an OEM's own housekeeping — and reports it here and nowhere
     * else. The permission still reads as granted afterwards, so nothing on any screen would
     * ever show that Odo has quietly stopped listening.
     *
     * The app-launch rebind cannot cover this: by the time anyone notices, the reason they
     * would open the app is the thing that stopped working.
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        HLogger.tag(TAG).w("listener_disconnected")
        PaymentNotices.markConnected(false)
        PaymentNotices.onUnbindRequested(null)
        runCatching { requestRebind(ComponentName(this, RefuelNotificationListenerService::class.java)) }
    }

    private companion object {
        const val TAG = "RefuelListener"
    }
}
