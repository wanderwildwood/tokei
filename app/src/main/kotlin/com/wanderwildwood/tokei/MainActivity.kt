package com.wanderwildwood.tokei

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.tokei.alarm.Engine
import com.wanderwildwood.tokei.alarm.Notifier
import com.wanderwildwood.tokei.ui.ClockApp
import com.wanderwildwood.tokei.ui.monochrome

class MainActivity : ComponentActivity() {

    /** A part to open, and whether to start a new alarm there, from an intent; taken once. */
    private val opening = mutableStateOf<Opening?>(null)

    data class Opening(val tab: Int, val newAlarm: Boolean = false, val hour: Int = -1, val minute: Int = -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.channels(this)
        if (savedInstanceState == null) opening.value = openingIn(intent)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                ClockApp(opening = opening.value, onOpened = { opening.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openingIn(intent)?.let { opening.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app is also a chance to catch up: if an alarm was lost, this is where it is
        // written down as missed, and the alarms are set again.
        Engine.sync(this)
    }

    private fun openingIn(intent: Intent?): Opening? {
        val tab = intent?.getIntExtra(EXTRA_TAB, -1) ?: -1
        if (tab < 0) return null
        return Opening(
            tab = tab,
            newAlarm = intent?.getBooleanExtra(EXTRA_NEW_ALARM, false) == true,
            hour = intent?.getIntExtra(EXTRA_HOUR, -1) ?: -1,
            minute = intent?.getIntExtra(EXTRA_MINUTE, -1) ?: -1,
        )
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_NEW_ALARM = "new_alarm"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val TAB_ALARMS = 0
        const val TAB_TIMERS = 1
        const val TAB_STOPWATCH = 2
        const val TAB_WORLD = 3
    }
}
