package com.wanderwildwood.tokei.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.wanderwildwood.tokei.MainActivity
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.data.Missed
import com.wanderwildwood.tokei.data.Ring
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.data.Store
import com.wanderwildwood.tokei.data.Why

/**
 * The notifications: one for what is ringing, which brings up the ringing screen over the lock
 * screen and has Snooze and Stop on it, and one listing what was missed.
 */
object Notifier {

    const val RINGING = "ringing"
    const val MISSED = "missed"
    const val RINGING_ID = 1
    private const val MISSED_ID = 2

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_ringing_about)
                // The service plays the sound and the vibration, on the alarm stream.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun build(context: Context, r: Ring): Notification {
        channels(context)
        val store = Store.get(context)
        val title: String
        val text: String
        if (r.timer) {
            val t = store.timer(r.id)
            title = context.getString(R.string.ring_timer_done)
            text = t?.let { Engine.timerLabel(context, it) }.orEmpty()
        } else {
            val a = store.alarm(r.id)
            title = Times.time(context, r.due)
            text = a?.label?.ifBlank { null } ?: context.getString(R.string.alarm)
        }
        val b = NotificationCompat.Builder(context, RINGING)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            // Not setSilent: it groups the notification so as to hold back its alert, which
            // stops the full-screen intent too. The channel has no sound of its own anyway.
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(screen(context))
            .setFullScreenIntent(screen(context), true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (r.timer) {
            b.addAction(0, context.getString(R.string.ring_add_minute), action(context, ActionReceiver.TIMER_ADD, r.id))
            b.addAction(0, context.getString(R.string.ring_stop), action(context, ActionReceiver.TIMER_STOP, r.id))
        } else {
            val n = Settings(context).snoozeMinutes
            b.addAction(0, context.getString(R.string.ring_snooze_for, Times.minutes(context, n)), action(context, ActionReceiver.SNOOZE, r.id))
            b.addAction(0, context.getString(R.string.ring_stop), action(context, ActionReceiver.DISMISS, r.id))
        }
        return b.build()
    }

    /** Posts the ringing notification without the service, when the service could not be started. */
    fun ringing(context: Context, r: Ring) {
        context.getSystemService(NotificationManager::class.java).notify(RINGING_ID, build(context, r))
    }

    fun cancelRinging(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(RINGING_ID)
    }

    /** Says what was missed: the newest first, every one with its reason. */
    fun missed(context: Context, list: List<Missed>) {
        if (list.isEmpty()) return
        channels(context)
        val all = Store.get(context).missed()
        val lines = all.take(6).map { line(context, it) }
        val b = NotificationCompat.Builder(context, MISSED)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(context.resources.getQuantityString(R.plurals.missed_title, all.size, all.size))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true)
            .setContentIntent(Engine.show(context, MainActivity.TAB_ALARMS))
        context.getSystemService(NotificationManager::class.java).notify(MISSED_ID, b.build())
    }

    fun cancelMissed(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(MISSED_ID)
    }

    /** "Missed 06:30, Wake up: the app was stopped". */
    fun line(context: Context, m: Missed): String {
        val what = if (m.timer) context.getString(R.string.missed_timer, m.label) else m.label.ifBlank { context.getString(R.string.alarm) }
        val why = context.getString(
            when (m.why) {
                Why.STOPPED -> R.string.why_stopped
                Why.PHONE_OFF -> R.string.why_phone_off
                Why.UNANSWERED -> R.string.why_unanswered
                Why.UNKNOWN -> R.string.why_unknown
            },
        )
        return context.getString(R.string.missed_line, Times.whenShort(context, m.due), what, why)
    }

    private fun action(context: Context, what: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ActionReceiver::class.java)
            .setAction(what)
            // Each one its own intent, so one never stands in for another.
            .setData(Uri.parse("tokei://ring/$id/$what"))
            .putExtra(ActionReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun screen(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        RINGING_ID,
        RingActivity.intent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
