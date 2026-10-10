package com.wanderwildwood.tokei.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Settings

/** The alarm clock or the timer alarm going off: catch up, ring, set the next. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ALARM && intent.action != TIMER) return
        Log.i("tokei", "system alarm went off: ${intent.action}")
        Engine.sync(context)
    }

    companion object {
        const val ALARM = "com.wanderwildwood.tokei.ALARM"
        const val TIMER = "com.wanderwildwood.tokei.TIMER"
    }
}

/** Snooze, Stop and a minute more, pressed on the notification, from the lock screen or anywhere. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        when (intent.action) {
            SNOOZE -> Engine.snooze(context, id, Settings(context).snoozeMinutes)
            DISMISS -> Engine.dismiss(context, id)
            TIMER_STOP -> Engine.stopTimer(context, id)
            TIMER_ADD -> Engine.timerAction(context, id) { t, now -> Plan.addMinute(t, now) }
        }
    }

    companion object {
        const val SNOOZE = "com.wanderwildwood.tokei.SNOOZE"
        const val DISMISS = "com.wanderwildwood.tokei.DISMISS"
        const val TIMER_STOP = "com.wanderwildwood.tokei.TIMER_STOP"
        const val TIMER_ADD = "com.wanderwildwood.tokei.TIMER_ADD"
        const val EXTRA_ID = "id"
    }
}

/**
 * Everything that clears or moves the alarms: the phone starting up, this app being updated,
 * the clock or the time zone being changed, the permission for exact alarms being given or taken
 * away. Each one sets them again from what is stored, and says what was missed meanwhile.
 */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("tokei", "system: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Whatever was ringing when the phone went off is not ringing now.
                Ringer.clear(context)
                Notifier.channels(context)
                Engine.sync(context)
            }
            Intent.ACTION_LOCALE_CHANGED -> Notifier.channels(context)
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> Engine.sync(context)
        }
    }
}
