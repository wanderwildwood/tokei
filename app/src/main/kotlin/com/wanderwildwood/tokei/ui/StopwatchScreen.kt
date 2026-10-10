package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.data.Store

/**
 * The stopwatch, in the ringing screen's shapes: the time large, a round button for Lap (Reset
 * once stopped) and the black pill for Start and Stop. While it runs it shows whole seconds, a
 * redraw a second; stopped, and in the laps, it shows hundredths.
 */
@Composable
fun StopwatchScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val version by Store.version.collectAsState()
    var w by remember(version) { androidx.compose.runtime.mutableStateOf(settings.stopwatch) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(w.running) {
        now = System.currentTimeMillis()
        while (w.running) {
            delay1s()
            now = System.currentTimeMillis()
        }
    }
    fun set(next: com.wanderwildwood.tokei.data.Stopwatch) {
        settings.stopwatch = next
        w = next
        now = System.currentTimeMillis()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        TextMMD(
            text = Times.clock(w.total(now), fine = !w.running),
            fontSize = 64.sp,
            lineHeight = 70.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (w.laps.isNotEmpty()) {
            TextMMD(
                text = stringResource(R.string.stopwatch_this_lap, w.laps.size + 1, Times.clock(w.current(now), fine = !w.running)),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val canLap = w.running
            val canReset = !w.running && w.total(now) > 0
            Box(
                Modifier
                    .size(104.dp)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                    .clickable(enabled = canLap || canReset) {
                        val t = System.currentTimeMillis()
                        if (canLap) set(w.lap(t)) else set(com.wanderwildwood.tokei.data.Stopwatch())
                    },
                contentAlignment = Alignment.Center,
            ) {
                TextMMD(
                    text = stringResource(if (w.running || w.total(now) == 0L) R.string.stopwatch_lap else R.string.stopwatch_reset),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            ButtonMMD(
                onClick = {
                    val t = System.currentTimeMillis()
                    set(if (w.running) w.stop(t) else w.start(t))
                },
                modifier = Modifier.width(160.dp).height(64.dp),
            ) {
                TextMMD(
                    text = stringResource(if (w.running) R.string.stopwatch_stop else R.string.stopwatch_start),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val laps = w.lapTimes()
        LazyColumnMMD(modifier = Modifier.weight(1f).fillMaxWidth()) {
            for (i in laps.indices.reversed()) {
                item(key = i) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        TextMMD(text = stringResource(R.string.stopwatch_lap_n, i + 1), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextMMD(text = Times.clock(laps[i], fine = true), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        TextMMD(text = Times.clock(w.laps[i], fine = true), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    Dotted()
                }
            }
        }
    }
}

private suspend fun delay1s() = kotlinx.coroutines.delay(1_000L - System.currentTimeMillis() % 1_000L)
