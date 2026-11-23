package com.wanderwildwood.tokei.ui

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightClockTest {

    @Test
    fun `a press of the light key goes round the three and back`() {
        assertEquals(NightLight.LOW, NightLight.AS_SET.next())
        assertEquals(NightLight.OFF, NightLight.LOW.next())
        assertEquals(NightLight.AS_SET, NightLight.OFF.next())
    }

    @Test
    fun `a stored light that is not one of the three is the phone's own`() {
        assertEquals(NightLight.AS_SET, NightLight.of(-1))
        assertEquals(NightLight.AS_SET, NightLight.of(7))
        assertEquals(NightLight.OFF, NightLight.of(2))
    }

    @Test
    fun `as set leaves the light to the phone and off asks for none`() {
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, NightLight.AS_SET.brightness)
        assertEquals(0f, NightLight.OFF.brightness)
        assertTrue(NightLight.LOW.brightness > 0f && NightLight.LOW.brightness < 0.2f)
    }

    @Test
    fun `the figures fit the Kompakt on its side with and without AM or PM`() {
        // 752 x 480 px at density 210 is 573 x 366 dp; less the padding and the keys.
        val w = 541f
        val h = 286f
        val with = nightFigureSize(w, h, ampm = true)
        val without = nightFigureSize(w, h, ampm = false)
        assertTrue(with * 3.7f <= w + 0.01f)
        assertTrue(without * 3.1f <= w + 0.01f)
        assertTrue(with <= h && without <= h)
        assertTrue(without >= with)
    }

    @Test
    fun `a low wide space is limited by its height`() {
        assertEquals(72f, nightFigureSize(2000f, 100f, ampm = false), 0.001f)
    }
}
