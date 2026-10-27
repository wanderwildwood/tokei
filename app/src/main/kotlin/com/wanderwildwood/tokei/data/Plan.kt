package com.wanderwildwood.tokei.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When things ring, worked out from the clock alone. Nothing here touches Android, so all of it
 * is tested on the build machine (PlanTest).
 *
 * The repeat-day arithmetic is Fossify Clock's (getTimeOfNextAlarm, with its day bits), moved to
 * java.time so a night when the clocks change is handled the way Medicine handles it: a time
 * the clocks skip rings when they come back, and a time they repeat rings once.
 */
object Plan {

    /** An alarm or a timer found up to this late still rings. Later than this it is written down as missed. */
    const val RING_WITHIN_MS = 10 * 60_000L

    /** At most this many missed times are written down for one alarm at once: a month away is not thirty notices. */
    const val MISSED_KEPT = 20

    const val EVERY_DAY = 127
    const val WEEKDAYS = 31
    const val WEEKEND = 96

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    /** The first moment after [after] when [time] falls on one of [days] (any day, for 0), in [zone]. */
    fun next(time: LocalTime, days: Int, after: Long, zone: ZoneId): Long? {
        val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        for (i in 0L..8L) {
            val date = start.plusDays(i)
            if (days != 0 && days and bit(date.dayOfWeek) == 0) continue
            val at = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
            if (at > after) return at
        }
        return null
    }

    fun next(a: Alarm, after: Long, zone: ZoneId): Long? = next(a.time, a.days, after, zone)

    /**
     * Every time [a] came due after it was last caught up, to [now], oldest first. A once alarm
     * has one at most. A time that was skipped is not among them: it neither rings nor is missed.
     */
    fun dues(a: Alarm, now: Long, zone: ZoneId, limit: Int = 500): List<Long> {
        if (!a.enabled) return emptyList()
        val out = ArrayList<Long>()
        var t = a.from
        while (out.size < limit) {
            val n = next(a, t, zone) ?: break
            if (n > now) break
            if (n != a.skip) out += n
            if (!a.repeats) break
            t = n
        }
        return out
    }

    /** The next time [a] is set to ring by its days, a skipped one passed over; null when it is off. */
    fun scheduled(a: Alarm, zone: ZoneId): Long? {
        if (!a.enabled) return null
        val n = next(a, a.from, zone) ?: return null
        return if (n == a.skip && a.repeats) next(a, n, zone) else n
    }

    /** The time a press of "Skip the next one" would skip: the next by its days, for a repeating alarm. */
    fun skippable(a: Alarm, zone: ZoneId): Long? =
        if (a.enabled && a.repeats) next(a, a.from, zone) else null

    /** What catching an alarm up finds: the time to ring for now, if one is recent enough, and the ones missed. */
    data class Due(val ring: Long?, val missed: List<Long>)

    fun catchUp(a: Alarm, now: Long, zone: ZoneId): Due {
        val all = dues(a, now, zone)
        val last = all.lastOrNull() ?: return Due(null, emptyList())
        return if (now - last <= RING_WITHIN_MS) Due(last, all.dropLast(1)) else Due(null, all)
    }

    /** When [a] rings next, a snooze included; null when it will not ring. */
    fun nextRing(a: Alarm, zone: ZoneId): Long? {
        val snooze = a.snoozedUntil.takeIf { it > 0 }
        return listOfNotNull(snooze, scheduled(a, zone)).minOrNull()
    }

    /** Whether the person changed when [new] rings, as against only what it is called or sounds like. */
    fun scheduleChanged(old: Alarm?, new: Alarm): Boolean =
        old == null || old.hour != new.hour || old.minute != new.minute || old.days != new.days

    /**
     * [edited] as it is kept after Save. When and on which days are unchanged, so is the state
     * it was in: on or off, caught up to where it was, snoozed or not, with its skip. A once
     * alarm that is waiting for this evening stays this evening; one that is snoozed stays
     * snoozed. A change of time or days starts it afresh, on, from [now].
     */
    fun saved(old: Alarm?, edited: Alarm, now: Long): Alarm =
        if (old != null && !scheduleChanged(old, edited)) {
            edited.copy(enabled = old.enabled, from = old.from, snoozedUntil = old.snoozedUntil, skip = old.skip)
        } else {
            edited.copy(enabled = true, from = now, snoozedUntil = 0, skip = 0)
        }

    /** [a] after its switch is pressed to [on]: a snooze goes with it, whichever way. */
    fun switched(a: Alarm, on: Boolean, now: Long): Alarm =
        a.copy(enabled = on, from = now, snoozedUntil = 0, skip = 0)

    /** [a] with its snooze cancelled and nothing else changed: a repeating alarm rings next at its time. */
    fun unsnoozed(a: Alarm): Alarm = a.copy(snoozedUntil = 0)

    /** The next alarm to ring, and whether it is a snooze. */
    data class Wake(val at: Long, val alarm: Alarm, val snoozed: Boolean)

    fun nextWake(alarms: List<Alarm>, zone: ZoneId): Wake? =
        alarms.mapNotNull { a -> nextRing(a, zone)?.let { Wake(it, a, it == a.snoozedUntil) } }.minByOrNull { it.at }

    /**
     * The time handed to the system for [wake]. Mudita's home screen reads the next alarm from the
     * system, and takes a time that is not a whole ten milliseconds to be a snoozed one, as the
     * stock Alarm app sets them; so a snooze is set a millisecond late and anything else on the
     * dot.
     */
    fun systemTime(wake: Wake, now: Long): Long {
        val at = maxOf(wake.at, now + 1_000L)
        return at - at % 10 + if (wake.snoozed) 1 else 0
    }

    /** A snooze of [minutes] from [now], on a whole second. */
    fun snooze(now: Long, minutes: Int): Long {
        val at = now + minutes * 60_000L
        return at - at % 1_000L
    }

    /**
     * Why something due at [due] did not ring. A force-stop (by hand, or by DuraSpeed) after the
     * app last ran ([since]) and before the time cancelled it; a phone that started after the time
     * was off or restarting then. Anything else is said as not known, rather than guessed.
     */
    fun why(due: Long, since: Long, lastStop: Long, bootAt: Long): Why = when {
        lastStop in (since + 1)..due -> Why.STOPPED
        bootAt > due -> Why.PHONE_OFF
        else -> Why.UNKNOWN
    }

    // ---------------------------------------------------------------- timers

    fun remaining(t: Timer, now: Long): Long = when (t.state) {
        Timer.RUNNING -> maxOf(0L, t.endsAt - now)
        Timer.PAUSED -> t.left
        Timer.RINGING -> 0L
        else -> t.seconds * 1_000L
    }

    fun start(t: Timer, now: Long): Timer = when (t.state) {
        Timer.RUNNING -> t
        Timer.PAUSED -> t.copy(state = Timer.RUNNING, endsAt = now + t.left, left = 0, startedAt = now)
        else -> t.copy(state = Timer.RUNNING, endsAt = now + t.seconds * 1_000L, left = 0, startedAt = now)
    }

    fun pause(t: Timer, now: Long): Timer =
        if (t.state == Timer.RUNNING) t.copy(state = Timer.PAUSED, left = remaining(t, now), endsAt = 0) else t

    fun reset(t: Timer): Timer = t.copy(state = Timer.IDLE, endsAt = 0, left = 0)

    /** A minute more: on a running or paused timer, and on one ringing, which starts it again for a minute. */
    fun addMinute(t: Timer, now: Long): Timer = when (t.state) {
        Timer.RUNNING -> t.copy(endsAt = t.endsAt + 60_000L)
        Timer.PAUSED -> t.copy(left = t.left + 60_000L)
        Timer.RINGING -> t.copy(state = Timer.RUNNING, endsAt = now + 60_000L, left = 0, startedAt = now)
        else -> t
    }

    fun due(t: Timer, now: Long): Boolean = t.state == Timer.RUNNING && t.endsAt <= now

    fun nextTimer(timers: List<Timer>): Long? = timers.filter { it.state == Timer.RUNNING }.minOfOrNull { it.endsAt }
}
