package com.wanderwildwood.tokei

import com.wanderwildwood.tokei.alarm.Notifier
import com.wanderwildwood.tokei.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test

class RingingChannelTest {

    @Test
    fun ringingWithoutTheServiceSoundsAndBuzzesAsSet() {
        assertEquals(Notifier.RINGING_SOUND, Notifier.ringingChannel("", vibrate = true))
        assertEquals(Notifier.RINGING_SOUND_STILL, Notifier.ringingChannel("", vibrate = false))
        assertEquals(Notifier.RINGING_SOUND_STILL, Notifier.ringingChannel("content://sounds/7", vibrate = false))
        assertEquals(Notifier.RINGING_BUZZ, Notifier.ringingChannel(Alarm.SILENT, vibrate = true))
        assertEquals(Notifier.RINGING, Notifier.ringingChannel(Alarm.SILENT, vibrate = false))
    }
}
