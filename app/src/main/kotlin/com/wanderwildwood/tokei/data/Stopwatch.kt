package com.wanderwildwood.tokei.data

/**
 * The stopwatch, as a value: whether it is [running], what it had counted before it was last
 * started ([banked]), when that was ([since]), and the total at each lap. Fossify Clock's stopwatch
 * kept this in memory and lost it with the app; kept as wall-clock times in the settings, it
 * carries on through the app being closed or stopped.
 */
data class Stopwatch(val running: Boolean = false, val since: Long = 0, val banked: Long = 0, val laps: List<Long> = emptyList()) {

    fun total(now: Long): Long = banked + if (running) maxOf(0L, now - since) else 0L

    fun start(now: Long): Stopwatch = if (running) this else copy(running = true, since = now)

    fun stop(now: Long): Stopwatch = if (!running) this else copy(running = false, since = 0, banked = total(now))

    fun lap(now: Long): Stopwatch = if (!running) this else copy(laps = laps + total(now))

    /** Each lap's own length, first lap first. */
    fun lapTimes(): List<Long> = laps.mapIndexed { i, t -> t - if (i == 0) 0L else laps[i - 1] }

    /** The lap under way: the time since the last lap was pressed. */
    fun current(now: Long): Long = total(now) - (laps.lastOrNull() ?: 0L)

    fun encode(): String = "${if (running) 1 else 0};$since;$banked;" + laps.joinToString(",")

    companion object {
        fun decode(s: String): Stopwatch {
            val p = s.split(';')
            if (p.size != 4) return Stopwatch()
            return Stopwatch(
                running = p[0] == "1",
                since = p[1].toLongOrNull() ?: 0L,
                banked = p[2].toLongOrNull() ?: 0L,
                laps = p[3].split(',').mapNotNull { it.toLongOrNull() },
            )
        }
    }
}
