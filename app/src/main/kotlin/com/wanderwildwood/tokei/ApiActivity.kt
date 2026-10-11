package com.wanderwildwood.tokei

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.util.Log
import android.widget.Toast
import com.wanderwildwood.tokei.alarm.Engine
import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.data.Store
import com.wanderwildwood.tokei.data.Timer
import java.time.DayOfWeek
import java.util.Calendar

/**
 * What other apps ask a clock to do, answered the way Android's own clock answers it: set an
 * alarm or a timer, show them, snooze or stop what is ringing, and the stopwatch actions some
 * launchers and assistants send to the clock by name. It draws nothing; it does the thing, or
 * opens the app at the right place, and closes.
 */
class ApiActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handle(intent)
        } catch (e: Exception) {
            Log.e("tokei", "could not answer ${intent?.action}", e)
        }
        finish()
    }

    private fun handle(intent: Intent) {
        Log.i("tokei", "asked: ${intent.action}")
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> setAlarm(intent)
            AlarmClock.ACTION_SHOW_ALARMS -> open(MainActivity.TAB_ALARMS)
            AlarmClock.ACTION_DISMISS_ALARM -> {
                val r = Settings(this).ringing.lastOrNull { !it.timer }
                if (r != null) Engine.dismiss(this, r.id) else open(MainActivity.TAB_ALARMS)
            }
            AlarmClock.ACTION_SNOOZE_ALARM -> {
                val r = Settings(this).ringing.lastOrNull { !it.timer }
                val minutes = intent.getIntExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, Settings(this).snoozeMinutes)
                if (r != null) Engine.snooze(this, r.id, minutes.coerceIn(1, 24 * 60)) else open(MainActivity.TAB_ALARMS)
            }
            AlarmClock.ACTION_SET_TIMER -> setTimer(intent)
            AlarmClock.ACTION_SHOW_TIMERS, DESK_SHOW_TIMERS -> open(MainActivity.TAB_TIMERS)
            AlarmClock.ACTION_DISMISS_TIMER -> {
                val those = Settings(this).ringing.filter { it.timer }
                if (those.isEmpty()) open(MainActivity.TAB_TIMERS) else those.forEach { Engine.stopTimer(this, it.id) }
            }
            DESK_SHOW_STOPWATCH -> open(MainActivity.TAB_STOPWATCH)
            DESK_START_STOPWATCH, DESK_PAUSE_STOPWATCH, DESK_LAP_STOPWATCH, DESK_RESET_STOPWATCH -> {
                val s = Settings(this)
                val now = System.currentTimeMillis()
                val w = s.stopwatch
                s.stopwatch = when (intent.action) {
                    DESK_START_STOPWATCH -> w.start(now)
                    DESK_PAUSE_STOPWATCH -> w.stop(now)
                    DESK_LAP_STOPWATCH -> w.lap(now)
                    else -> com.wanderwildwood.tokei.data.Stopwatch()
                }
                Store.bump()
                open(MainActivity.TAB_STOPWATCH)
            }
            DESK_SHOW_CLOCK -> open(MainActivity.TAB_WORLD)
            else -> open(MainActivity.TAB_ALARMS)
        }
    }

    private fun setAlarm(intent: Intent) {
        if (!intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
            startActivity(main(MainActivity.TAB_ALARMS).putExtra(MainActivity.EXTRA_NEW_ALARM, true))
            return
        }
        val hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, 0).coerceIn(0, 23)
        val minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0).coerceIn(0, 59)
        val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
        val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS).orEmpty().fold(0) { acc, d -> acc or bitOfCalendarDay(d) }
        val ringtone = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE)
        val sound = when {
            ringtone == null -> ""
            ringtone == AlarmClock.VALUE_RINGTONE_SILENT -> Alarm.SILENT
            else -> ringtone
        }
        val vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true)

        // The same alarm asked for again is switched on, not made twice, as Android's clock does.
        val same = Store.get(this).alarms().firstOrNull { it.hour == hour && it.minute == minute && it.days == days && it.label == label }
        val alarm = (same ?: Alarm(hour = hour, minute = minute, days = days, label = label, sound = sound, vibrate = vibrate))
        Engine.save(this, alarm)
        Toast.makeText(this, getString(R.string.alarm_set_for, Times.time(this, java.time.LocalTime.of(hour, minute))), Toast.LENGTH_SHORT).show()
        if (!intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) open(MainActivity.TAB_ALARMS)
    }

    private fun setTimer(intent: Intent) {
        val length = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, -1)
        if (length !in 1..24 * 3600) {
            open(MainActivity.TAB_TIMERS)
            return
        }
        val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
        // Started on the spot, as Start in the app does, so not kept: it leaves the list once it
        // is stopped, unless Keep is pressed while it runs.
        val id = Engine.saveTimer(this, Timer(seconds = length, label = label, kept = false))
        Engine.timerAction(this, id) { t, now -> Plan.start(t, now) }
        Toast.makeText(this, getString(R.string.timer_started, Times.length(this, length)), Toast.LENGTH_SHORT).show()
        if (!intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) open(MainActivity.TAB_TIMERS)
    }

    private fun main(tab: Int): Intent = Intent(this, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_TAB, tab)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun open(tab: Int) = startActivity(main(tab))

    /** java.util.Calendar's Sunday 1 to Saturday 7, as this app's day bits. */
    private fun bitOfCalendarDay(d: Int): Int = when (d) {
        Calendar.MONDAY -> Plan.bit(DayOfWeek.MONDAY)
        Calendar.TUESDAY -> Plan.bit(DayOfWeek.TUESDAY)
        Calendar.WEDNESDAY -> Plan.bit(DayOfWeek.WEDNESDAY)
        Calendar.THURSDAY -> Plan.bit(DayOfWeek.THURSDAY)
        Calendar.FRIDAY -> Plan.bit(DayOfWeek.FRIDAY)
        Calendar.SATURDAY -> Plan.bit(DayOfWeek.SATURDAY)
        Calendar.SUNDAY -> Plan.bit(DayOfWeek.SUNDAY)
        else -> 0
    }

    companion object {
        // The actions Android's own clock (DeskClock) takes by name, which some launchers and
        // assistants send to whatever answers them.
        private const val DESK = "com.android.deskclock.action."
        const val DESK_SHOW_TIMERS = DESK + "SHOW_TIMERS"
        const val DESK_SHOW_STOPWATCH = DESK + "SHOW_STOPWATCH"
        const val DESK_START_STOPWATCH = DESK + "START_STOPWATCH"
        const val DESK_PAUSE_STOPWATCH = DESK + "PAUSE_STOPWATCH"
        const val DESK_LAP_STOPWATCH = DESK + "LAP_STOPWATCH"
        const val DESK_RESET_STOPWATCH = DESK + "RESET_STOPWATCH"
        const val DESK_SHOW_CLOCK = DESK + "SHOW_CLOCK"
    }
}
