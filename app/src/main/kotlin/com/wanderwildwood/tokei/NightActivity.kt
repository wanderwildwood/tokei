package com.wanderwildwood.tokei

import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.ui.NightClock
import com.wanderwildwood.tokei.ui.NightLight
import com.wanderwildwood.tokei.ui.houseTypography
import com.wanderwildwood.tokei.ui.monochrome

/**
 * The night clock: the time across the whole screen with the phone stood on its side, kept on
 * through the night. It is its own screen so that it can lie sideways (the rest of the app is
 * upright) and so that the light it turns down is this screen's alone: leaving it gives the
 * phone its own light back, with nothing to undo.
 */
class NightActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Only the time: the status bar would sit along the long edge with its own clock in it.
        window.insetsController?.hide(WindowInsets.Type.statusBars())
        val settings = Settings(this)
        light(NightLight.of(settings.nightLight))
        setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) {
                NightClock(settings = settings, onLight = ::light)
            }
        }
    }

    /** Sets this screen's light; the phone's own setting is left as it is. */
    private fun light(level: NightLight) {
        window.attributes = window.attributes.apply { screenBrightness = level.brightness }
    }
}
