package com.wanderwildwood.tokei.data

/**
 * Alarms put together under a name, such as Office and Home, so that a whole set of them can be
 * switched on or off, or have its next day skipped, at one press. A group is nothing more than
 * the name its alarms share: there is no table of groups, and a group with no alarms left in it
 * is gone. An alarm with no group ("") is listed on its own, as before.
 *
 * Everything here is worked out from the alarms alone, so it can be tested without a phone; the
 * changes themselves go through [com.wanderwildwood.tokei.alarm.Engine], one alarm at a time,
 * the same way a single switch goes.
 */
object Groups {

    const val MAX_NAME = 30

    /** A stretch of the alarm list: ungrouped alarms have [name] "" and no heading over them. */
    data class Section(val name: String, val alarms: List<Alarm>)

    /**
     * The alarms as the Alarm tab lists them: the ungrouped ones first, in the order of the
     * clock as before, then each group under its name, the names in alphabetical order and the
     * alarms in each in the order of the clock. [alarms] arrive in clock order already.
     */
    fun sections(alarms: List<Alarm>): List<Section> {
        val loose = alarms.filter { it.group.isBlank() }
        val named = alarms.filter { it.group.isNotBlank() }
            .groupBy { it.group }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (name, list) -> Section(name, list) }
        return (if (loose.isEmpty()) emptyList() else listOf(Section("", loose))) + named
    }

    /** The group names in use, for choosing one in an alarm's settings. */
    fun names(alarms: List<Alarm>): List<String> =
        alarms.map { it.group }.filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    /**
     * A typed name made tidy: spaces trimmed and run together, cut to [MAX_NAME], and spelt the
     * way a group already in use spells it, so "office" joins "Office" rather than making a
     * second group beside it.
     */
    fun clean(typed: String, existing: List<String>): String {
        val name = typed.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim()
        if (name.isEmpty()) return ""
        return existing.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
    }

    /** The heading's switch reads on while any alarm in the group is on. */
    fun isOn(group: List<Alarm>): Boolean = group.any { it.enabled }

    /**
     * The alarms the heading's switch has to change to put the whole group [on] or off: only
     * those not that way already, so an alarm left as it was keeps its skip and its snooze.
     */
    fun toSwitch(group: List<Alarm>, on: Boolean): List<Long> =
        group.filter { it.enabled != on }.map { it.id }

    /** The alarms "Skip next" applies to: the repeating ones that are on. */
    fun skippable(group: List<Alarm>): List<Alarm> = group.filter { it.enabled && it.repeats }

    /** "Skip next" reads on when every alarm it applies to already skips its next time. */
    fun isSkipping(group: List<Alarm>): Boolean {
        val s = skippable(group)
        return s.isNotEmpty() && s.all { it.skip > 0 }
    }

    /** The alarms to change so the group's next times are skipped ([on]) or rung again. */
    fun toSkip(group: List<Alarm>, on: Boolean): List<Long> =
        skippable(group).filter { (it.skip > 0) != on }.map { it.id }
}
