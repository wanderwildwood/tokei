package com.wanderwildwood.tokei.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tokei.MainActivity
import com.wanderwildwood.tokei.R

/** An alarm or a timer being made rather than changed. */
const val NEW = 0L

/**
 * The whole app: the stock Alarm app's list, with three more parts beside it under MMD's tabs
 * (timers, the stopwatch, the world clock), and a page that opens over them to make or change an
 * alarm or a timer, to add a city, or for the settings.
 */
@Composable
fun ClockApp(opening: MainActivity.Opening?, onOpened: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var alarm by rememberSaveable { mutableStateOf<Long?>(null) }
    var alarmPreset by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    var timer by rememberSaveable { mutableStateOf<Long?>(null) }
    var addCity by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(opening) {
        val o = opening ?: return@LaunchedEffect
        alarm = null
        timer = null
        addCity = false
        settings = false
        tab = o.tab.coerceIn(0, 3)
        if (o.newAlarm) {
            alarmPreset = if (o.hour >= 0) o.hour to o.minute.coerceAtLeast(0) else null
            alarm = NEW
        }
        onOpened()
    }

    val a = alarm
    val t = timer
    when {
        a != null -> AlarmEditScreen(id = a, preset = alarmPreset, onDone = {
            alarm = null
            alarmPreset = null
        })
        t != null -> TimerEditScreen(id = t, onDone = { timer = null })
        addCity -> CityPicker(onDone = { addCity = false })
        settings -> SettingsScreen(onBack = { settings = false }, onAbout = { about = true })
        else -> {
            BackHandler(enabled = tab != 0) { tab = 0 }
            Main(
                tab = tab,
                onTab = { tab = it },
                onAlarm = { alarm = it },
                onTimer = { timer = it },
                onAddCity = { addCity = true },
                onSettings = { settings = true },
                onAbout = { about = true },
            )
        }
    }
    if (about) AboutDialog(onDismiss = { about = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Main(
    tab: Int,
    onTab: (Int) -> Unit,
    onAlarm: (Long) -> Unit,
    onTimer: (Long) -> Unit,
    onAddCity: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings)
                    BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Tabs(tab, listOf(R.string.tab_alarms, R.string.tab_timers, R.string.tab_stopwatch, R.string.tab_world).map { stringResource(it) }, onTab)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> AlarmsScreen(onEdit = onAlarm)
                    1 -> TimersScreen(onEdit = onTimer)
                    2 -> StopwatchScreen()
                    else -> WorldScreen(onAdd = onAddCity)
                }
            }
        }
    }
}

/**
 * The four parts, in MMD's tab look: the chosen one bold over a black bar, a rule under the row.
 * Drawn here rather than with TabMMD, whose padding leaves a quarter of this screen too narrow
 * for "Stopwatch".
 */
@Composable
private fun Tabs(chosen: Int, labels: List<String>, onTab: (Int) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(52.dp)) {
        labels.forEachIndexed { i, label ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = i == chosen, role = Role.Tab, onClick = { onTab(i) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TextMMD(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (i == chosen) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(3.dp).background(if (i == chosen) ink else MaterialTheme.colorScheme.surface))
            }
        }
    }
    HorizontalDividerMMD(thickness = 2.dp)
}
