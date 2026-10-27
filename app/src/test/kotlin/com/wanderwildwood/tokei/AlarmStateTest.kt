package com.wanderwildwood.tokei

import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.Groups
import com.wanderwildwood.tokei.data.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the switch, Save and a snooze do to an alarm, above all to a one-time alarm. */
class AlarmStateTest {

    private val ny = ZoneId.of("America/New_York")

    private fun at(d: Int, h: Int, mi: Int = 0): Long =
        ZonedDateTime.of(2026, 10, d, h, mi, 0, 0, ny).toInstant().toEpochMilli()

    private fun local(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(ny).toLocalDateTime().toString()

    private val once = Alarm(id = 1, hour = 7, minute = 0, label = "Wake", from = at(10, 22), enabled = true)

    @Test fun savingOnlyTheNameKeepsAOnceAlarmWhereItWas() {
        // Set last night for 07:00; edited at 06:00 for its name only: still 07:00 today.
        val saved = Plan.saved(once, once.copy(label = "Gym"), at(11, 6))
        assertEquals(once.from, saved.from)
        assertTrue(saved.enabled)
        assertEquals("2026-10-11T07:00", local(Plan.scheduled(saved, ny)!!))
    }

    @Test fun savingOnlyTheSoundKeepsASnoozeAndAnOffSwitch() {
        val snoozed = once.copy(enabled = false, snoozedUntil = at(11, 7, 10))
        val a = Plan.saved(snoozed, snoozed.copy(sound = "content://x/1", soundTitle = "Chime"), at(11, 7, 5))
        assertEquals(snoozed.snoozedUntil, a.snoozedUntil)
        assertFalse(a.enabled)
        val off = once.copy(enabled = false)
        assertFalse(Plan.saved(off, off.copy(vibrate = false), at(11, 6)).enabled)
    }

    @Test fun savingANewTimeSwitchesItOnFromNowAndDropsTheSnooze() {
        val snoozed = once.copy(enabled = false, snoozedUntil = at(11, 7, 10), skip = 5)
        val a = Plan.saved(snoozed, snoozed.copy(hour = 8), at(11, 7, 5))
        assertTrue(a.enabled)
        assertEquals(0L, a.snoozedUntil)
        assertEquals(0L, a.skip)
        assertEquals("2026-10-11T08:00", local(Plan.scheduled(a, ny)!!))
    }

    @Test fun savingNewDaysCountsAsAChangeOfSchedule() {
        assertTrue(Plan.scheduleChanged(once, once.copy(days = Plan.WEEKDAYS)))
        assertFalse(Plan.scheduleChanged(once, once.copy(label = "x", group = "g")))
        assertTrue(Plan.scheduleChanged(null, once))
    }

    @Test fun aSnoozedOnceAlarmReadsOnAndOffCancelsItWithoutMovingIt() {
        val snoozed = once.copy(enabled = false, snoozedUntil = at(11, 7, 10))
        assertTrue(snoozed.isOn)
        val off = Plan.switched(snoozed, false, at(11, 7, 5))
        assertFalse(off.isOn)
        assertEquals(0L, off.snoozedUntil)
        assertNull(Plan.nextRing(off, ny))
    }

    @Test fun cancellingASnoozeLeavesTheAlarmAsItWas() {
        val daily = once.copy(days = Plan.EVERY_DAY, snoozedUntil = at(11, 7, 10))
        val a = Plan.unsnoozed(daily)
        assertTrue(a.enabled)
        assertEquals(0L, a.snoozedUntil)
        assertEquals(daily.from, a.from)
        assertEquals("2026-10-12T07:00", local(Plan.scheduled(a.copy(from = at(11, 7, 5)), ny)!!))
        // A once alarm that rang stays off.
        val rang = once.copy(enabled = false, snoozedUntil = at(11, 7, 10))
        assertNull(Plan.nextRing(Plan.unsnoozed(rang), ny))
    }

    @Test fun switchingAnOnceAlarmOffAndOnAgainEarlierTheSameDayKeepsItToday() {
        val off = Plan.switched(once, false, at(11, 5))
        assertNull(Plan.nextRing(off, ny))
        val on = Plan.switched(off, true, at(11, 5, 1))
        assertEquals("2026-10-11T07:00", local(Plan.nextRing(on, ny)!!))
    }

    @Test fun aGroupSwitchTreatsASnoozedOnceAlarmAsOn() {
        val snoozed = once.copy(id = 2, enabled = false, snoozedUntil = at(11, 7, 10))
        val off = once.copy(id = 3, enabled = false)
        assertTrue(Groups.isOn(listOf(snoozed, off)))
        assertEquals(listOf(2L), Groups.toSwitch(listOf(snoozed, off), false))
        assertEquals(listOf(3L), Groups.toSwitch(listOf(snoozed, off), true))
    }
}
