package com.hopcape.odo.core.platform.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hopcape.odo.core.platform.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Posts a detected fill with Confirm and Edit, and runs Confirm without opening the app.
 *
 * The receiver is registered at runtime rather than in the manifest. A manifest-declared
 * receiver is one the OS can wake the process for at any time, which is more than this needs:
 * the notification only exists while the collector that posted it is running, so a receiver
 * that lives exactly as long as the process is the honest scope. It also means the whole
 * feature stays out of the manifest while its flag is off.
 *
 * Edit opens the app rather than deep-linking to the confirm step. The draft rides along as an
 * extra, and the app shell reads it on start — a deep link would need a route the OS owns, and
 * this way the same intent works from a cold start.
 */
internal class AndroidDetectedFillNotifier(
    private val context: Context,
) : DetectedFillNotifier {

    /**
     * Lives as long as the process, like the receiver it runs work for.
     *
     * A confirm tap arrives on the main thread from a broadcast; the write it triggers is a
     * suspending database call, and there is no caller left to suspend by then.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var registered: BroadcastReceiver? = null

    override suspend fun show(notice: DetectedFillNotice, onConfirm: suspend (String) -> Unit) {
        if (!canPost()) return
        ensureChannel()
        registerConfirmReceiver(onConfirm)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            // A custom body, so the two actions can be pills. `addAction` is deliberately not
            // used: the platform draws those as borderless capitalised text and offers no
            // styling hook at all. `DecoratedCustomViewStyle` keeps the system's own header —
            // app icon, name, timestamp — above whatever this draws, which is what Android 12
            // and later require of a custom notification anyway.
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView(notice))
            .setCustomBigContentView(expandedView(notice))
            .setSmallIcon(R.drawable.ic_notification_odo)
            .setColor(ContextCompat.getColor(context, R.color.refuel_detected_accent))
            // Tell the launcher the accent is the brand rather than a hint, so the icon reads
            // as Odo's in a shade full of other apps.
            .setColorized(false)
            // A fill is worth surfacing as it happens: the owner is standing at the pump, and
            // a row that quietly joins the pile is one they confirm days later, if at all.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            // Nothing here is private beyond what the payment app already posted above it,
            // and a lock screen that hides the numbers makes Confirm unusable from the lock
            // screen — which is the whole point of the notification.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(editIntent(notice))
            .build()

        // One id for every detection, so a newer one replaces the older rather than joining
        // it in the shade. Per-notice ids stacked them up, and Android's ranker auto-bundles
        // four or more notifications from one app behind a summary it generates itself — a
        // summary with no custom view, which draws as a plain row with none of the actions
        // this notification exists for. That is the "plain notification" this feature was
        // reported as showing.
        //
        // Nothing is lost by replacing. The notification was never the record: `pending_fills`
        // holds every unanswered detection and the in-app prompt offers the ones still open.
        // This is a shortcut to the newest question, not a list of all of them.
        NotificationManagerCompat.from(context).notify(DETECTED_FILL_NOTIFICATION_ID, notification)
    }

    override fun dismiss(noticeId: String) {
        // The id is ignored: there is only ever one of these on screen. Callers still pass
        // one because they know *which* question was answered, which is what `pending_fills`
        // needs — the shade only needs to know there is one fewer.
        NotificationManagerCompat.from(context).cancel(DETECTED_FILL_NOTIFICATION_ID)
    }

    /**
     * One receiver for the process, replaced rather than stacked.
     *
     * Registering per notification would leave a receiver behind for every fill the owner
     * ever ignored, and each of them would fire on the next confirm.
     */
    private fun registerConfirmReceiver(onConfirm: suspend (String) -> Unit) {
        registered?.let { runCatching { context.unregisterReceiver(it) } }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val payload = intent?.getStringExtra(EXTRA_DRAFT) ?: return
                val noticeId = intent.getStringExtra(EXTRA_NOTICE_ID)
                scope.launch {
                    onConfirm(payload)
                    noticeId?.let(::dismiss)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_CONFIRM),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = receiver
    }

    /**
     * The collapsed row: one line of body, and the two pills.
     *
     * The actions are on the collapsed view too, not only the expanded one. A shade with
     * several notifications in it collapses everything below the first, and a fill the owner
     * has to expand before they can confirm has already cost them the tap this exists to save.
     */
    private fun collapsedView(notice: DetectedFillNotice): RemoteViews =
        buildView(notice, bodyLines = 1)

    /** The expanded row, where both body lines fit — the second is the odometer to check. */
    private fun expandedView(notice: DetectedFillNotice): RemoteViews =
        buildView(notice, bodyLines = 3)

    private fun buildView(notice: DetectedFillNotice, bodyLines: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_detected_fill).apply {
            setTextViewText(R.id.refuel_notification_title, notice.title)
            setTextViewText(R.id.refuel_notification_body, notice.body)
            setInt(R.id.refuel_notification_body, "setMaxLines", bodyLines)
            setTextViewText(R.id.refuel_notification_confirm, notice.confirmLabel)
            setTextViewText(R.id.refuel_notification_edit, notice.editLabel)
            val open = editIntent(notice)
            // A draft that cannot be written yet sends the owner into the app rather than
            // broadcasting a write that would fail silently. If the app cannot be opened
            // either, the button is left inert rather than wired to a write that will not
            // happen — a dead button beats one that eats the fill.
            setOnClickPendingIntent(
                R.id.refuel_notification_confirm,
                if (notice.oneTapConfirm) confirmIntent(notice) else open,
            )
            open?.let { setOnClickPendingIntent(R.id.refuel_notification_edit, it) }
        }

    private fun confirmIntent(notice: DetectedFillNotice): PendingIntent {
        val intent = Intent(ACTION_CONFIRM)
            .setPackage(context.packageName)
            .putExtra(EXTRA_DRAFT, notice.draftPayload)
            .putExtra(EXTRA_NOTICE_ID, notice.id)
        return PendingIntent.getBroadcast(
            context,
            notice.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Opens the app carrying the draft.
     *
     * `FLAG_MUTABLE` is deliberately not used: nothing outside the app needs to add to this
     * intent, and an immutable one cannot be rewritten by whoever holds it.
     */
    private fun editIntent(notice: DetectedFillNotice): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launch.putExtra(EXTRA_DRAFT, notice.draftPayload)
        return PendingIntent.getActivity(
            context,
            notice.id.hashCode() + 1,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** From Android 13 the owner has to grant this, and posting without it throws. */
    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // HIGH so the row can peek while the owner is still at the pump. A channel's
        // importance is fixed at creation and the owner owns it afterwards — raising it in a
        // later release does nothing for anyone who already has the channel.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.refuel_detected_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.refuel_detected_channel_description)
            // No sound. A fill is not urgent enough to interrupt, and the payment app has
            // already made a noise a second earlier.
            setSound(null, null)
            enableVibration(false)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    internal companion object {
        /**
         * The one notification id detected fills ever use.
         *
         * A constant on purpose — see [show]. Anything derived from the notice would let two
         * detections sit in the shade together and hand the ranker a reason to bundle them.
         */
        const val DETECTED_FILL_NOTIFICATION_ID = 0x0D0F111

        const val CHANNEL_ID = "refuel_detected"

        /** Private to the app — the receiver is registered `NOT_EXPORTED`. */
        const val ACTION_CONFIRM = "com.hopcape.odo.REFUEL_CONFIRM"

        /**
         * The serialized draft, carried by both the confirm broadcast and the launch intent.
         *
         * The same literal as `RefuelDeepLinks.EXTRA_DRAFT`, which is what reads it on the way
         * back in. It is repeated rather than shared because `:core:platform` sits below the
         * feature that owns the meaning of the payload, and the two are pinned together by a
         * test rather than by a dependency.
         */
        const val EXTRA_DRAFT = "odo.refuel.draft"
        const val EXTRA_NOTICE_ID = "odo.refuel.notice_id"
    }
}
