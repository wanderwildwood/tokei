package com.wanderwildwood.tokei

import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.ui.figuresChanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyBuzzTest {
    @Test fun buzzIsOffToBeginWith() {
        assertFalse(Settings.BUZZ_DEFAULT)
        assertEquals("buzz", Settings.BUZZ)
    }

    @Test fun aFigureTypedOrTakenAwayIsAKeyPress() {
        assertTrue(figuresChanged("07", "5", 2))
        assertTrue(figuresChanged("07", "0", 2))
        assertTrue(figuresChanged("7", "74", 2))
    }

    @Test fun nothingChangingIsNotAKeyPress() {
        assertFalse(figuresChanged("07", "07", 2))
        assertFalse(figuresChanged("07", "0 7", 2))
    }
}
