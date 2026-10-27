package com.wanderwildwood.tokei.alarm

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.ApplicationExitInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.wanderwildwood.tokei.MainActivity
import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.DuraSpeed
import com.wanderwildwood.tokei.data.Missed
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Ring
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.data.Store
import com.wanderwildwood.tokei.data.Timer
import com.wanderwildwood.tokei.data.Why
import com.wanderwildwood.tokei.glance.GlanceProvider
import java.time.ZoneId

/**
 * Everything that makes an alarm or a timer ring, and everything a press on one changes.
 *
 * Medicine's way, not Fossify Clock's: there is one system alarm for the next alarm clock and one
 * for the next timer, and whenever either goes off, or the app is opened, or the phone starts, or
 * the clock or the time zone changes, [sync] catches every alarm and timer up to now. What came
 * due lately rings; what came due too long ago to ring is written down as missed, with the reason
 * when it can be known. So an alarm the system dropped - the app force-stopped by DuraSpeed, the
 * phone off - is never lost without a word.
 */
object Engine {

    private const val TAG = "tokei"
    private val lock = Any()

    fun sync(context: Context, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val store = Store.get(context)
        val settings = Settings(context)
        val zone = ZoneId.systemDefault()

        DuraSpeed.newStop(context, settings)?.let {
            Log.w(TAG, "stopped by the system at $it; alarms set before then were cancelled")
            settings.stoppedAt = it
        }
        val lastStop = lastStop(context)
        val bootAt = now - SystemClock.elapsedRealtime()

        val rings = mutableListOf<Ring>()
        val missed = mutableListOf<Missed>()

        for (found in store.alarms()) {
            // A clock set back leaves an alarm caught up to a moment that has not come yet, and
            // nothing would ring until it did. Starting again from now is safe.
            var a = if (found.from > now) found.copy(from = now) else found
            val since = a.from

            if (a.snoozedUntil in 1..now) {
                if (now - a.snoozedUntil <= Plan.RING_WITHIN_MS) {
                    rings += Ring(false, a.id, a.snoozedUntil)
                } else {
                    missed += Missed(timer = false, label = a.label, due = a.snoozedUntil, why = Plan.why(a.snoozedUntil, since, lastStop, bootAt))
                }
                a = a.copy(snoozedUntil = 0)
            }

            val due = Plan.catchUp(a, now, zone)
            due.missed.takeLast(Plan.MISSED_KEPT).forEach {
                missed += Missed(timer = false, label = a.label, due = it, why = Plan.why(it, since, lastStop, bootAt))
            }
            due.ring?.let { rings += Ring(false, a.id, it) }
            if ((due.ring != null || due.missed.isNotEmpty()) && !a.repeats) a = a.copy(enabled = false)
            if (a.skip in 1..now) a = a.copy(skip = 0)
            a = a.copy(from = now)
            if (a != found) store.save(a)
        }

        for (t in store.timers()) {
            if (!Plan.due(t, now)) continue
            if (now - t.endsAt <= Plan.RING_WITHIN_MS) {
                rings += Ring(true, t.id, t.endsAt)
                store.save(t.copy(state = Timer.RINGING, endsAt = 0))
            } else {
                missed += Missed(timer = true, label = timerLabel(context, t), due = t.endsAt, why = Plan.why(t.endsAt, t.startedAt, lastStop, bootAt))
                store.save(Plan.reset(t))
            }
        }

        if (missed.isNotEmpty()) {
            missed.forEach { store.addMissed(it) }
            Log.w(TAG, "missed ${missed.size}: " + missed.joinToString { "${it.due} ${it.why}" })
            Notifier.missed(context, missed)
        }
        if (rings.isNotEmpty()) {
            Log.i(TAG, "ringing: " + rings.joinToString { it.encode() })
            Ringer.add(context, settings, rings, now)
        }

        schedule(context, store, now, zone)
        GlanceProvider.changed(context)
    }

    /**
     * The newest force-stop the system remembers for this app, by hand or by DuraSpeed; 0 for
     * none. Swiping the app out of Recents is recorded under the same reason as "remove task",
     * and cancels nothing, so it is not counted.
     */
    private fun lastStop(context: Context): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        return runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 0) }
            .getOrDefault(emptyList())
            .filter { it.reason == ApplicationExitInfo.REASON_USER_REQUESTED && it.description?.contains("remove task") != true }
            .maxOfOrNull { it.timestamp } ?: 0L
    }

    /** Sets the system's alarm clock for the next alarm, and an exact alarm for the next timer, or clears them. */
    private fun schedule(context: Context, store: Store, now: Long, zone: ZoneId) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = am.canScheduleExactAlarms()

        val wake = Plan.nextWake(store.alarms(), zone)
        val alarmPi = pending(context, AlarmReceiver.ALARM)
        if (wake == null) {
            am.cancel(alarmPi)
            Log.i(TAG, "no alarm to set")
        } else {
            val at = Plan.systemTime(wake, now)
            if (exact) {
                // An alarm clock is exact, is let through Doze, wakes the phone early to deliver
                // it, and is what the status bar, the lock screen and Mudita's home screen show.
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show(context, MainActivity.TAB_ALARMS)), alarmPi)
                Log.i(TAG, "alarm clock set for $at (alarm ${wake.alarm.id}${if (wake.snoozed) ", snoozed" else ""})")
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmPi)
                Log.w(TAG, "no exact alarms allowed; inexact alarm set for $at")
            }
        }

        val end = Plan.nextTimer(store.timers())
        val timerPi = pending(context, AlarmReceiver.TIMER)
        if (end == null) {
            am.cancel(timerPi)
        } else {
            val at = maxOf(end, now + 500L)
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timerPi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timerPi)
            Log.i(TAG, "timer alarm set for $at")
        }
    }

    private fun reschedule(context: Context) =
        schedule(context, Store.get(context), System.currentTimeMillis(), ZoneId.systemDefault())

    private fun changed(context: Context) {
        reschedule(context)
        GlanceProvider.changed(context)
    }

    // ---------------------------------------------------------------- alarms

    /**
     * Saves an alarm, new or changed. New, or with its time or days changed, it is switched on and
     * starts from now; with only its name, group or sound changed it keeps the state it was in
     * (see [Plan.saved]). Returns its id.
     */
    fun save(context: Context, a: Alarm): Long = synchronized(lock) {
        val store = Store.get(context)
        val old = if (a.id != 0L) store.alarm(a.id) else null
        val id = store.save(Plan.saved(old, a, System.currentTimeMillis()))
        if (Plan.scheduleChanged(old, a)) Ringer.remove(context, Ring(false, id, 0))
        changed(context)
        id
    }

    fun setEnabled(context: Context, id: Long, on: Boolean) = setEnabled(context, listOf(id), on)

    /**
     * Switches alarms on or off: one from its own switch, or a whole group from its heading.
     * Both come this way, so a group is set and scheduled exactly as its alarms would be one by
     * one, and the alarms are set again once, after the last of them.
     */
    fun setEnabled(context: Context, ids: List<Long>, on: Boolean) = synchronized(lock) {
        val store = Store.get(context)
        for (id in ids) {
            val a = store.alarm(id) ?: continue
            store.save(Plan.switched(a, on, System.currentTimeMillis()))
            if (!on) Ringer.remove(context, Ring(false, id, 0))
        }
        changed(context)
    }

    fun delete(context: Context, id: Long) = synchronized(lock) {
        Store.get(context).deleteAlarm(id)
        Ringer.remove(context, Ring(false, id, 0))
        changed(context)
    }

    /** Skips the next time of a repeating alarm, or, with [on] false, takes the skip back. */
    fun skipNext(context: Context, id: Long, on: Boolean) = skipNext(context, listOf(id), on)

    /** The same for several alarms at once, such as a group's. */
    fun skipNext(context: Context, ids: List<Long>, on: Boolean) = synchronized(lock) {
        val store = Store.get(context)
        for (id in ids) {
            val a = store.alarm(id) ?: continue
            val skip = if (on) Plan.skippable(a, ZoneId.systemDefault()) ?: 0L else 0L
            store.save(a.copy(skip = skip))
        }
        changed(context)
    }

    /** Gives group [from] a new name; a name already in use joins the two, and "" ungroups them. */
    fun renameGroup(context: Context, from: String, to: String) = synchronized(lock) {
        if (from.isBlank() || from == to) return@synchronized
        Store.get(context).renameGroup(from, to)
    }

    /** Puts a ringing (or snoozed) alarm off for [minutes]. */
    fun snooze(context: Context, id: Long, minutes: Int) = synchronized(lock) {
        val store = Store.get(context)
        val a = store.alarm(id) ?: return@synchronized
        val until = Plan.snooze(System.currentTimeMillis(), minutes)
        store.save(a.copy(snoozedUntil = until))
        Log.i(TAG, "alarm $id snoozed $minutes min, until $until")
        Ringer.remove(context, Ring(false, id, 0))
        changed(context)
    }

    /** Stops a ringing alarm, and any snooze of it. A once alarm is already off: it rang. */
    fun dismiss(context: Context, id: Long) = synchronized(lock) {
        val store = Store.get(context)
        store.alarm(id)?.let { if (it.snoozedUntil != 0L) store.save(Plan.unsnoozed(it)) }
        Log.i(TAG, "alarm $id dismissed")
        Ringer.remove(context, Ring(false, id, 0))
        changed(context)
    }

    /** Cancels a snooze from the alarm list; the alarm itself is as it was (a repeating one still rings at its time). */
    fun cancelSnooze(context: Context, id: Long) = dismiss(context, id)

    // ---------------------------------------------------------------- timers

    fun saveTimer(context: Context, t: Timer): Long = synchronized(lock) {
        val id = Store.get(context).save(t)
        changed(context)
        id
    }

    fun deleteTimer(context: Context, id: Long) = synchronized(lock) {
        Store.get(context).deleteTimer(id)
        Ringer.remove(context, Ring(true, id, 0))
        changed(context)
    }

    fun timerAction(context: Context, id: Long, action: (Timer, Long) -> Timer) = synchronized(lock) {
        val store = Store.get(context)
        val t = store.timer(id) ?: return@synchronized
        val now = System.currentTimeMillis()
        val next = action(t, now)
        store.save(next)
        if (t.state == Timer.RINGING && next.state != Timer.RINGING) Ringer.remove(context, Ring(true, id, 0))
        changed(context)
    }

    /** Stops a ringing timer, ready to start again from its full length. */
    fun stopTimer(context: Context, id: Long) = timerAction(context, id) { t, _ -> Plan.reset(t) }

    fun timerLabel(context: Context, t: Timer): String =
        t.label.ifBlank { com.wanderwildwood.tokei.Times.length(context, t.seconds) }

    // ---------------------------------------------------------------- unanswered

    /**
     * Ringing for as long as the setting allows with nobody answering: it stops, and each one is
     * written down as rung but not answered. An alarm is not snoozed on its own.
     */
    fun unanswered(context: Context) = synchronized(lock) {
        val store = Store.get(context)
        val settings = Settings(context)
        val those = settings.ringing
        if (those.isEmpty()) return@synchronized
        val missed = those.map { r ->
            if (r.timer) {
                val t = store.timer(r.id)
                t?.let { store.save(Plan.reset(it)) }
                Missed(timer = true, label = t?.let { timerLabel(context, it) }.orEmpty(), due = r.due, why = Why.UNANSWERED)
            } else {
                Missed(timer = false, label = store.alarm(r.id)?.label.orEmpty(), due = r.due, why = Why.UNANSWERED)
            }
        }
        missed.forEach { store.addMissed(it) }
        Log.w(TAG, "rang ${settings.ringMinutes} min with no answer: " + those.joinToString { it.encode() })
        Ringer.clear(context)
        Notifier.missed(context, missed)
        changed(context)
    }

    // ---------------------------------------------------------------- intents

    private fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (action == AlarmReceiver.TIMER) 1 else 0,
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** What the system's next-alarm opens when it is pressed: this app, at its alarms. */
    fun show(context: Context, tab: Int): PendingIntent = PendingIntent.getActivity(
        context,
        100 + tab,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
