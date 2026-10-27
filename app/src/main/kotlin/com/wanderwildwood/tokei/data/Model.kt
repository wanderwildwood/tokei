package com.wanderwildwood.tokei.data

import java.time.LocalTime

/**
 * An alarm. [days] is a set of weekdays as bits, Monday 1 to Sunday 64, the way Fossify Clock
 * counts them; 0 rings once.
 *
 * [from] is how far it has been caught up: every time of it up to that moment has rung or been
 * written down as missed, and only later ones are still to come. [snoozedUntil] is 0 when it is
 * not snoozed. [skip] is one coming time of a repeating alarm that is not to ring; 0 for none.
 * [group] is the name of the group it is in, such as "Office"; "" for none (see [Groups]).
 */
data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val label: String = "",
    /** "" for the phone's alarm sound, [SILENT] for none, otherwise a sound's address. */
    val sound: String = "",
    val soundTitle: String = "",
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    val from: Long = 0,
    val snoozedUntil: Long = 0,
    val skip: Long = 0,
    val group: String = "",
) {
    val repeats: Boolean get() = days != 0

    /**
     * What its switch shows. A once alarm switches itself off when it rings, so while it is
     * snoozed it is [enabled] false and still going to ring: the switch reads on, and turning it
     * off cancels the snooze, instead of turning it on moving it to tomorrow.
     */
    val isOn: Boolean get() = enabled || snoozedUntil > 0
    val time: LocalTime get() = LocalTime.of(hour, minute)

    companion object {
        const val SILENT = "silent"
    }
}

/**
 * A countdown. It keeps its length, so it can be started again; while it runs, [endsAt] is when
 * it rings, and while it is paused, [left] is what remains. [startedAt] is when it was last
 * started or resumed, which is what a missed timer is measured against.
 */
data class Timer(
    val id: Long = 0,
    val seconds: Int,
    val label: String = "",
    val state: Int = IDLE,
    val endsAt: Long = 0,
    val left: Long = 0,
    val startedAt: Long = 0,
    val sound: String = "",
    val soundTitle: String = "",
    val vibrate: Boolean = true,
    /** In the list for good. A timer started on the fly is not, until Keep is pressed: it is
     *  there while it runs and gone once it is stopped or reset. */
    val kept: Boolean = true,
) {
    companion object {
        const val IDLE = 0
        const val RUNNING = 1
        const val PAUSED = 2
        const val RINGING = 3
    }
}

/** An alarm or a timer that did not ring, or rang and was not answered, and why, as far as it is known. */
data class Missed(
    val id: Long = 0,
    val timer: Boolean,
    val label: String,
    val due: Long,
    val why: Why,
)

enum class Why { STOPPED, PHONE_OFF, UNANSWERED, UNKNOWN }

/** Something ringing now: an alarm (at its time or after a snooze) or a timer that ran out. */
data class Ring(val timer: Boolean, val id: Long, val due: Long) {
    fun encode(): String = (if (timer) "t" else "a") + ":" + id + ":" + due

    fun same(other: Ring) = timer == other.timer && id == other.id

    companion object {
        fun decode(s: String): Ring? {
            val p = s.split(':')
            if (p.size != 3) return null
            val id = p[1].toLongOrNull() ?: return null
            val due = p[2].toLongOrNull() ?: return null
            return Ring(p[0] == "t", id, due)
        }

        fun encodeAll(list: List<Ring>): String = list.joinToString(",") { it.encode() }

        fun decodeAll(s: String): List<Ring> = s.split(',').filter { it.isNotBlank() }.mapNotNull(::decode)
    }
}
