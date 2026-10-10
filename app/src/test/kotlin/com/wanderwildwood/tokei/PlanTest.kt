package com.wanderwildwood.tokei

import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Ring
import com.wanderwildwood.tokei.data.Stopwatch
import com.wanderwildwood.tokei.data.Timer
import com.wanderwildwood.tokei.data.Why
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class PlanTest {

    private val ny = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0, zone: ZoneId = ny): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private fun local(ms: Long, zone: ZoneId = ny) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString()

    private fun days(vararg d: DayOfWeek) = d.fold(0) { acc, x -> acc or Plan.bit(x) }

    // ---------------------------------------------------------------- the next time

    @Test fun aOnceAlarmRingsLaterTodayOrTomorrow() {
        val a = Alarm(hour = 6, minute = 30, from = at(2026, 10, 10, 5))
        assertEquals("2026-10-10T06:30", local(Plan.next(a, a.from, ny)!!))
        val b = a.copy(from = at(2026, 10, 10, 7))
        assertEquals("2026-10-11T06:30", local(Plan.next(b, b.from, ny)!!))
    }

    @Test fun theMinuteItIsSetAtIsNotStillToCome() {
        val a = Alarm(hour = 6, minute = 30, from = at(2026, 10, 10, 6, 30))
        assertEquals("2026-10-11T06:30", local(Plan.next(a, a.from, ny)!!))
    }

    @Test fun repeatDaysPickTheNextOfThem() {
        // 2026-10-10 is a Saturday.
        val weekdays = Alarm(hour = 7, minute = 0, days = Plan.WEEKDAYS, from = at(2026, 10, 10, 8))
        assertEquals("2026-10-12T07:00", local(Plan.next(weekdays, weekdays.from, ny)!!))
        val wed = Alarm(hour = 7, minute = 0, days = days(DayOfWeek.WEDNESDAY), from = at(2026, 10, 14, 7, 0))
        assertEquals("2026-10-21T07:00", local(Plan.next(wed, wed.from, ny)!!))
    }

    @Test fun aRepeatingAlarmSchedulesTheNextDayAfterItRings() {
        val daily = Alarm(hour = 6, minute = 30, days = Plan.EVERY_DAY, from = at(2026, 10, 10, 6, 30))
        assertEquals("2026-10-11T06:30", local(Plan.nextRing(daily, ny)!!))
    }

    @Test fun midnightBelongsToTheNewDay() {
        val wed = Alarm(hour = 0, minute = 0, days = days(DayOfWeek.WEDNESDAY), from = at(2026, 10, 6, 0, 1))
        // 2026-10-07 is a Wednesday: its midnight is the one between Tuesday and Wednesday.
        assertEquals("2026-10-07T00:00", local(Plan.next(wed, wed.from, ny)!!))
    }

    // ---------------------------------------------------------------- clock changes, New York

    @Test fun springForwardRingsASkippedTimeWhenTheClockComesBack() {
        // 2026-03-08: 02:00 EST jumps to 03:00 EDT. 02:30 does not exist that night.
        val a = Alarm(hour = 2, minute = 30, days = Plan.EVERY_DAY, from = at(2026, 3, 7, 12))
        val first = Plan.next(a, a.from, ny)!!
        val second = Plan.next(a, first, ny)!!
        assertEquals("2026-03-08T03:30", local(first))
        assertEquals("2026-03-09T02:30", local(second))
        assertEquals(Duration.ofHours(23), Duration.ofMillis(second - first))
    }

    @Test fun fallBackRingsARepeatedTimeOnce() {
        // 2026-11-01: 02:00 EDT falls back to 01:00 EST, so 01:30 happens twice.
        val a = Alarm(hour = 1, minute = 30, days = Plan.EVERY_DAY, from = at(2026, 10, 31, 12))
        val dues = Plan.dues(a, at(2026, 11, 2, 12), ny)
        assertEquals(listOf("2026-11-01T01:30", "2026-11-02T01:30"), dues.map { local(it) })
    }

    @Test fun aTimeZoneChangeKeepsTheClockTime() {
        // Set for 07:00 in New York; the phone moves to Tokyo, where it is 01:00 the next day.
        // It rings at 07:00 Tokyo time, not at New York's 07:00.
        val a = Alarm(hour = 7, minute = 0, days = Plan.EVERY_DAY, from = at(2026, 10, 10, 12))
        val there = Plan.next(a, a.from, tokyo)!!
        assertEquals("2026-10-11T07:00", local(there, tokyo))
    }

    // ---------------------------------------------------------------- catching up

    @Test fun anAlarmOnTimeRings() {
        val a = Alarm(hour = 6, minute = 30, from = at(2026, 10, 10, 6))
        val due = Plan.catchUp(a, at(2026, 10, 10, 6, 30), ny)
        assertEquals(at(2026, 10, 10, 6, 30), due.ring)
        assertTrue(due.missed.isEmpty())
    }

    @Test fun anAlarmAFewMinutesLateStillRings() {
        val a = Alarm(hour = 6, minute = 30, from = at(2026, 10, 10, 6))
        val due = Plan.catchUp(a, at(2026, 10, 10, 6, 39), ny)
        assertEquals(at(2026, 10, 10, 6, 30), due.ring)
    }

    @Test fun anAlarmFoundTooLateIsMissedNotRung() {
        val a = Alarm(hour = 6, minute = 30, from = at(2026, 10, 10, 6))
        val due = Plan.catchUp(a, at(2026, 10, 10, 7, 0), ny)
        assertNull(due.ring)
        assertEquals(listOf(at(2026, 10, 10, 6, 30)), due.missed)
    }

    @Test fun afterALongStopEveryTimeIsMissedAndOnlyARecentOneRings() {
        val a = Alarm(hour = 6, minute = 30, days = Plan.EVERY_DAY, from = at(2026, 10, 7, 12))
        val due = Plan.catchUp(a, at(2026, 10, 10, 6, 31), ny)
        assertEquals(at(2026, 10, 10, 6, 30), due.ring)
        assertEquals(listOf("2026-10-08T06:30", "2026-10-09T06:30"), due.missed.map { local(it) })
    }

    @Test fun aSwitchedOffAlarmNeverComesDue() {
        val a = Alarm(hour = 6, minute = 30, enabled = false, from = at(2026, 10, 10, 6))
        assertTrue(Plan.dues(a, at(2026, 10, 12, 12), ny).isEmpty())
        assertNull(Plan.nextRing(a, ny))
    }

    @Test fun catchingUpFromItsOwnTimeFindsNothingTwice() {
        val a = Alarm(hour = 6, minute = 30, days = Plan.EVERY_DAY, from = at(2026, 10, 10, 6, 30))
        assertTrue(Plan.dues(a, at(2026, 10, 10, 6, 35), ny).isEmpty())
    }

    @Test fun moreThanFiveAlarmsAreAllScheduled() {
        val alarms = (0 until 8).map { Alarm(id = it.toLong() + 1, hour = 6, minute = it * 5, from = at(2026, 10, 10, 5)) }
        assertEquals(8, alarms.mapNotNull { Plan.nextRing(it, ny) }.distinct().size)
        assertEquals(1L, Plan.nextWake(alarms, ny)!!.alarm.id)
    }

    // ---------------------------------------------------------------- snooze and skip

    @Test fun snoozeIsWholeMinutesFromNowOnASecond() {
        val now = at(2026, 10, 10, 6, 30) + 12_345L
        assertEquals(at(2026, 10, 10, 6, 40) + 12_000L, Plan.snooze(now, 10))
        assertEquals(at(2026, 10, 10, 7, 30) + 12_000L, Plan.snooze(now, 60))
    }

    @Test fun aSnoozedAlarmIsTheNextThingToRing() {
        val a = Alarm(hour = 6, minute = 30, days = Plan.EVERY_DAY, from = at(2026, 10, 10, 6, 31), snoozedUntil = at(2026, 10, 10, 6, 45))
        val w = Plan.nextWake(listOf(a), ny)!!
        assertEquals(at(2026, 10, 10, 6, 45), w.at)
        assertTrue(w.snoozed)
    }

    @Test fun muditasHomeScreenSeesASnoozeAsOne() {
        val a = Alarm(hour = 6, minute = 30, from = at(2026, 10, 10, 6))
        val now = at(2026, 10, 10, 5)
        val plain = Plan.systemTime(Plan.nextWake(listOf(a), ny)!!, now)
        assertEquals(0L, plain % 10)
        val snoozed = a.copy(enabled = false, snoozedUntil = at(2026, 10, 10, 6, 40))
        val s = Plan.systemTime(Plan.nextWake(listOf(snoozed), ny)!!, now)
        assertTrue(s % 10 != 0L)
    }

    @Test fun aSkippedTimeNeitherRingsNorIsMissed() {
        val a0 = Alarm(hour = 6, minute = 30, days = Plan.EVERY_DAY, from = at(2026, 10, 10, 7))
        val skip = Plan.skippable(a0, ny)!!
        assertEquals("2026-10-11T06:30", local(skip))
        val a = a0.copy(skip = skip)
        assertEquals("2026-10-12T06:30", local(Plan.nextRing(a, ny)!!))
        val due = Plan.catchUp(a, at(2026, 10, 11, 6, 31), ny)
        assertNull(due.ring)
        assertTrue(due.missed.isEmpty())
    }

    // ---------------------------------------------------------------- why it was missed

    @Test fun aStopBetweenTheLastRunAndTheTimeIsTheReason() {
        val since = at(2026, 10, 9, 22)
        val due = at(2026, 10, 10, 6, 30)
        assertEquals(Why.STOPPED, Plan.why(due, since, lastStop = at(2026, 10, 9, 23), bootAt = at(2026, 10, 1, 0)))
    }

    @Test fun aStopBeforeTheAppLastRanIsNot() {
        val since = at(2026, 10, 9, 22)
        val due = at(2026, 10, 10, 6, 30)
        assertEquals(Why.UNKNOWN, Plan.why(due, since, lastStop = at(2026, 10, 9, 21), bootAt = at(2026, 10, 1, 0)))
    }

    @Test fun aPhoneStartedAfterTheTimeWasOff() {
        val due = at(2026, 10, 10, 6, 30)
        assertEquals(Why.PHONE_OFF, Plan.why(due, at(2026, 10, 9, 22), lastStop = 0, bootAt = at(2026, 10, 10, 7)))
    }

    // ---------------------------------------------------------------- timers

    @Test fun aTimerCountsDownPausesAndResumes() {
        val t0 = Timer(id = 1, seconds = 300)
        val now = 1_000_000L
        val running = Plan.start(t0, now)
        assertEquals(now + 300_000L, running.endsAt)
        assertEquals(240_000L, Plan.remaining(running, now + 60_000L))
        val paused = Plan.pause(running, now + 60_000L)
        assertEquals(240_000L, Plan.remaining(paused, now + 999_000L))
        val again = Plan.start(paused, now + 100_000L)
        assertEquals(now + 340_000L, again.endsAt)
        assertFalse(Plan.due(again, now + 339_999L))
        assertTrue(Plan.due(again, now + 340_000L))
    }

    @Test fun aMinuteMoreOnARingingTimerRunsItForAMinute() {
        val ringing = Timer(id = 1, seconds = 300, state = Timer.RINGING)
        val t = Plan.addMinute(ringing, 5_000L)
        assertEquals(Timer.RUNNING, t.state)
        assertEquals(65_000L, t.endsAt)
    }

    @Test fun twoTimersRunAtOnceAndTheSoonerIsNext() {
        val a = Plan.start(Timer(id = 1, seconds = 600), 0L)
        val b = Plan.start(Timer(id = 2, seconds = 120), 0L)
        assertEquals(120_000L, Plan.nextTimer(listOf(a, b)))
    }

    // ---------------------------------------------------------------- stopwatch and stored values

    @Test fun stopwatchLapsAndStops() {
        var w = Stopwatch().start(0L)
        w = w.lap(10_000L)
        w = w.lap(25_000L)
        w = w.stop(30_000L)
        assertEquals(30_000L, w.total(99_000L))
        assertEquals(listOf(10_000L, 15_000L), w.lapTimes())
        assertEquals(w, Stopwatch.decode(w.encode()))
        w = w.start(100_000L)
        assertEquals(35_000L, w.total(105_000L))
    }

    @Test fun ringingSurvivesBeingStored() {
        val list = listOf(Ring(false, 3, 123L), Ring(true, 5, 456L))
        assertEquals(list, Ring.decodeAll(Ring.encodeAll(list)))
        assertTrue(Ring.decodeAll("").isEmpty())
    }

    @Test fun clockText() {
        assertEquals("4:05", Times.clock(245_000L))
        assertEquals("1:02:03", Times.clock(3_723_000L))
        assertEquals("0:01.27", Times.clock(1_270L, fine = true))
        assertEquals("5:00", Times.clock(299_001L, roundUp = true))
    }
}
