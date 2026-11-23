package com.wanderwildwood.tokei.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.noise.NoiseService
import com.wanderwildwood.tokei.noise.Preset

/** The night clock's light: the phone's own, turned low, or off. */
enum class NightLight(val brightness: Float) {
    AS_SET(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE),
    LOW(0.05f),
    OFF(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF);

    /** The one a press of the key moves to. */
    fun next(): NightLight = values()[(ordinal + 1) % values().size]

    companion object {
        fun of(stored: Int): NightLight = values().getOrElse(stored) { AS_SET }
    }
}

/**
 * How tall the figures can be, in dp, to fill a space [width] by [height] without being cut:
 * five figures ("12:59") are about three of their own heights wide, and AM or PM beside them
 * takes a little more.
 */
internal fun nightFigureSize(width: Float, height: Float, ampm: Boolean): Float =
    minOf(width / if (ampm) 3.7f else 3.1f, height * 0.72f)

/**
 * The day above, the time as large as the screen lets it be, and three words along the foot
 * that are its buttons. "Backlight" steps through as the phone has it, low and off; the middle
 * one names the page it is on, "Black" or "White", and a press turns it to the other; "Noise"
 * turns the sleep noise on and off. They carry no frame and no other state. It is
 * drawn again once a minute, when the phone says the minute has turned, and at no other time.
 */
@Composable
fun NightClock(settings: Settings, onLight: (NightLight) -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                now = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        now = System.currentTimeMillis()
        onDispose { context.unregisterReceiver(receiver) }
    }

    var light by remember { mutableStateOf(NightLight.of(settings.nightLight)) }
    var black by remember { mutableStateOf(settings.nightBlack) }
    val noise by NoiseService.current.collectAsState()

    // Dark mode already swaps the two colours of every screen, so the page is drawn the other
    // way round while it is on, and comes out as chosen here.
    val drawBlack = black != DarkMode.on
    val page = if (drawBlack) Color.Black else Color.White
    val ink = if (drawBlack) Color.White else Color.Black

    Column(Modifier.fillMaxSize().background(page).padding(horizontal = 16.dp, vertical = 12.dp)) {
        TextMMD(
            text = nightDay(now),
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val (figures, ampm) = Times.split(context, now)
            val size = nightFigureSize(maxWidth.value, maxHeight.value, ampm.isNotEmpty()).dp
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                TextMMD(
                    text = figures,
                    color = ink,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = size.fixedSp(), lineHeight = size.fixedSp()),
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                if (ampm.isNotEmpty()) {
                    val small = size / 5
                    TextMMD(
                        text = ampm,
                        color = ink,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = small.fixedSp(), lineHeight = small.fixedSp()),
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.alignByBaseline().padding(start = 8.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            NightKey(
                text = stringResource(R.string.night_backlight),
                ink = ink,
                modifier = Modifier.weight(1f),
            ) {
                light = light.next()
                settings.nightLight = light.ordinal
                onLight(light)
            }
            NightKey(
                text = stringResource(if (black) R.string.night_black else R.string.night_white),
                ink = ink,
                modifier = Modifier.weight(1f),
            ) {
                black = !black
                settings.nightBlack = black
            }
            NightKey(
                text = stringResource(R.string.night_noise),
                ink = ink,
                modifier = Modifier.weight(1f),
            ) {
                // The sound and the length are the ones chosen on the Noise tab.
                if (noise.playing) NoiseService.stop(context)
                else NoiseService.start(context, Preset.of(settings.noisePreset), settings.noiseMinutes)
            }
        }
    }
}

/** The day the night clock is showing, as "Monday 23 November", in the phone's language. */
internal fun nightDay(nowMs: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", locale))

/**
 * A button at the foot of the night clock: its name as a plain word, no frame. A press moves it
 * to its next setting. The touch area is the full 48 dp and a third of the width.
 */
@Composable
private fun NightKey(text: String, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            text = text,
            color = ink,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** A size in dp as text size, whatever the phone's text size is set to: the figures fill the screen as it is. */
@Composable
private fun Dp.fixedSp() = with(LocalDensity.current) { toSp() }
