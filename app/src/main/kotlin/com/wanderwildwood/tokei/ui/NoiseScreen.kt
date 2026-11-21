package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.noise.NoiseService
import com.wanderwildwood.tokei.noise.Preset

/** The minutes the sleep noise can be set to; 0 plays until stopped. */
private val LENGTHS = listOf(30, 60, 120, 0)

/**
 * Sleep noise: a button to start and stop it, the sound, and how long before it fades out. Nothing
 * on the page moves while it plays; the line under the button says when it will end, not how
 * long is left.
 */
@Composable
fun NoiseScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val state by NoiseService.current.collectAsState()
    var preset by remember { mutableStateOf(Preset.of(settings.noisePreset)) }
    var minutes by remember { mutableIntStateOf(settings.noiseMinutes) }

    // Changing a choice while it plays starts it again on the new one.
    fun again() {
        if (state.playing) NoiseService.start(context, preset, minutes)
    }

    PagedColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp)) {
        item(key = "go") {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                ButtonMMD(
                    onClick = { if (state.playing) NoiseService.stop(context) else NoiseService.start(context, preset, minutes) },
                    modifier = Modifier.width(200.dp).height(64.dp),
                ) {
                    TextMMD(
                        text = stringResource(if (state.playing) R.string.noise_stop else R.string.noise_start),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val line = when {
                !state.playing -> stringResource(R.string.noise_idle)
                state.endsAt > 0L -> stringResource(R.string.noise_ends_at, Times.time(context, state.endsAt))
                else -> stringResource(R.string.noise_until_stopped)
            }
            TextMMD(text = line, style = helperStyle(), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        item(key = "h_sound") { Heading(stringResource(R.string.noise_sound)) }
        for (p in Preset.values()) {
            item(key = "p_${p.key}") {
                ChoiceRow(stringResource(NoiseService.presetName(p)), preset == p) {
                    preset = p
                    settings.noisePreset = p.key
                    again()
                }
            }
        }
        item(key = "h_len") { Heading(stringResource(R.string.noise_length), Modifier.padding(top = 12.dp)) }
        for (m in LENGTHS) {
            item(key = "m_$m") {
                ChoiceRow(lengthName(m), minutes == m) {
                    minutes = m
                    settings.noiseMinutes = m
                    again()
                }
            }
        }
        item(key = "note") {
            TextMMD(text = stringResource(R.string.noise_note), style = helperStyle(), modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun lengthName(m: Int): String = when (m) {
    0 -> stringResource(R.string.noise_len_stopped)
    30 -> stringResource(R.string.noise_len_30)
    60 -> stringResource(R.string.noise_len_60)
    else -> stringResource(R.string.noise_len_120)
}
