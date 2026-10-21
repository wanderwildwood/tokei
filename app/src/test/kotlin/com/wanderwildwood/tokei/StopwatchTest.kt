package com.wanderwildwood.tokei

import com.wanderwildwood.tokei.data.Stopwatch
import org.junit.Assert.assertEquals
import org.junit.Test

class StopwatchTest {

    @Test
    fun ticksOnItsOwnSecondsNotTheWallClocks() {
        // Started at x.7 s on the wall clock: the first whole second is a full second later.
        val w = Stopwatch().start(10_700)
        assertEquals(1_000L, w.untilNextSecond(10_700))
        assertEquals(400L, w.untilNextSecond(11_300))
        assertEquals(1_000L, w.untilNextSecond(11_700))
    }

    @Test
    fun carriesOnAcrossPauseAndResume() {
        // 1.25 s counted, stopped, started again at 50.0 s: 0.75 s to go to 0:02.
        val w = Stopwatch().start(0).stop(1_250).start(50_000)
        assertEquals(750L, w.untilNextSecond(50_000))
        assertEquals(2_000L, w.total(50_750))
        // Stopped, it still answers from what it banked.
        assertEquals(750L, w.stop(50_000).untilNextSecond(99_999))
    }

    @Test
    fun theLapLineTurnsFirstWhenItIsDue() {
        // A lap at 0.4 s: the lap under way turns at 1.4 s, before the total at 2.0 s.
        val w = Stopwatch().start(0).lap(400)
        assertEquals(400L, w.untilNextSecond(1_000))
        assertEquals(600L, w.untilNextSecond(1_400))
    }

    @Test
    fun twoTurnsCloseTogetherAreOneRedraw() {
        // A lap at 0.05 s: the lap turns 50 ms after the total, so both are drawn at the later.
        val w = Stopwatch().start(0).lap(50)
        assertEquals(750L, w.untilNextSecond(300))
        // Apart again by more than a tenth of a second, the sooner one leads.
        val v = Stopwatch().start(0).lap(500)
        assertEquals(200L, v.untilNextSecond(1_300))
    }
}
