package com.wanderwildwood.tokei

import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.Groups
import com.wanderwildwood.tokei.data.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsTest {

    private fun alarm(id: Long, hour: Int, group: String = "", on: Boolean = true, days: Int = Plan.WEEKDAYS, skip: Long = 0) =
        Alarm(id = id, hour = hour, minute = 0, days = days, enabled = on, group = group, skip = skip)

    // Store.alarms() hands them over in the order of the clock.
    private val list = listOf(
        alarm(1, 6, "Office"),
        alarm(2, 7),
        alarm(3, 7, "home"),
        alarm(4, 8, "Office", on = false),
        alarm(5, 9, "home", days = 0),
        alarm(6, 10),
    )

    @Test fun ungroupedFirstThenEachGroupByName() {
        val s = Groups.sections(list)
        assertEquals(listOf("", "home", "Office"), s.map { it.name })
        assertEquals(listOf(2L, 6L), s[0].alarms.map { it.id })
        assertEquals(listOf(3L, 5L), s[1].alarms.map { it.id })
        assertEquals(listOf(1L, 4L), s[2].alarms.map { it.id })
    }

    @Test fun noGroupsListsAsBefore() {
        val plain = list.map { it.copy(group = "") }
        val s = Groups.sections(plain)
        assertEquals(1, s.size)
        assertEquals(plain, s[0].alarms)
        assertTrue(Groups.sections(emptyList()).isEmpty())
    }

    @Test fun onlyGroupsLeavesNoEmptyLooseSection() {
        val s = Groups.sections(list.filter { it.group.isNotEmpty() })
        assertEquals(listOf("home", "Office"), s.map { it.name })
    }

    @Test fun namesAreDistinctAndSorted() {
        assertEquals(listOf("home", "Office"), Groups.names(list))
    }

    @Test fun aTypedNameIsTidiedAndJoinsTheSpellingInUse() {
        val names = listOf("Office", "Home")
        assertEquals("Office", Groups.clean("  office ", names))
        assertEquals("Night shift", Groups.clean(" Night   shift ", names))
        assertEquals("", Groups.clean("   ", names))
        assertEquals(Groups.MAX_NAME, Groups.clean("x".repeat(50), names).length)
    }

    @Test fun theSwitchIsOnWhileAnyAlarmIsOn() {
        val office = list.filter { it.group == "Office" }
        assertTrue(Groups.isOn(office))
        assertFalse(Groups.isOn(office.map { it.copy(enabled = false) }))
    }

    @Test fun switchingAGroupChangesOnlyTheAlarmsNotThatWayAlready() {
        val office = list.filter { it.group == "Office" }
        assertEquals(listOf(1L), Groups.toSwitch(office, on = false))
        // On is all on: the one that was off comes on, the one already on is left alone.
        assertEquals(listOf(4L), Groups.toSwitch(office, on = true))
        assertTrue(Groups.toSwitch(office.map { it.copy(enabled = true) }, on = true).isEmpty())
    }

    @Test fun skipNextTakesTheRepeatingAlarmsThatAreOn() {
        val home = list.filter { it.group == "home" }
        // 5 rings once, so it has no "next one" to skip.
        assertEquals(listOf(3L), Groups.skippable(home).map { it.id })
        assertEquals(listOf(3L), Groups.toSkip(home, on = true))
        val office = list.filter { it.group == "Office" }
        // 4 is off.
        assertEquals(listOf(1L), Groups.toSkip(office, on = true))
    }

    @Test fun skipNextReadsOnWhenEveryOneSkipsAndUndoesOnlyThose() {
        val g = listOf(alarm(1, 6, "A", skip = 111), alarm(2, 7, "A"), alarm(3, 8, "A", on = false))
        assertFalse(Groups.isSkipping(g))
        assertEquals(listOf(2L), Groups.toSkip(g, on = true))
        val all = g.map { if (it.enabled) it.copy(skip = 111) else it }
        assertTrue(Groups.isSkipping(all))
        assertEquals(listOf(1L, 2L), Groups.toSkip(all, on = false))
        assertFalse(Groups.isSkipping(listOf(alarm(9, 6, "B", days = 0))))
    }
}
