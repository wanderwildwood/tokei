package com.wanderwildwood.tokei

import org.junit.Assert.assertEquals
import org.junit.Test

class TimesSecondsTest {
    @Test fun secondsAlwaysTakeTwoFigures() {
        assertEquals("9:41:07", Times.withSeconds("9:41", 7))
        assertEquals("21:05:00", Times.withSeconds("21:05", 0))
        assertEquals("12:00:59", Times.withSeconds("12:00", 59))
    }
}
