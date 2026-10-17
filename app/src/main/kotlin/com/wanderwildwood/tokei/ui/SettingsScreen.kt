package com.wanderwildwood.tokei.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.alarm.SNOOZES
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.glance.GlanceProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Settings(context) }
    var v by remember { mutableIntStateOf(0) }
    var check by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        check++
        onPauseOrDispose { }
    }
    val problems = rememberProblems(check) { check++ }
    var pickSnooze by remember { mutableStateOf(false) }
    var pickRing by remember { mutableStateOf(false) }
    BackHandler { onBack() }

    // Read again after every change; [v] is only here to make that happen.
    val snooze = remember(v) { s.snoozeMinutes }
    val ring = remember(v) { s.ringMinutes }
    val rise = remember(v) { s.rise }
    val glance = remember(v) { s.onLockScreen }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.settings_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = { BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout) },
            )
        },
    ) { padding ->
        LazyColumnMMD(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp), isScrollbarVisible = false) {
            item { Spacer(Modifier.height(8.dp)) }
            problems.forEachIndexed { i, p -> item(key = "problem:$i") { ProblemCard(p) } }

            item(key = "snooze") {
                SettingRow(stringResource(R.string.settings_snooze), Times.minutes(context, snooze)) { pickSnooze = true }
            }
            item(key = "ring") {
                SettingRow(stringResource(R.string.settings_ring_for), stringResource(R.string.settings_ring_for_value, Times.minutes(context, ring))) { pickRing = true }
            }
            item(key = "rise") {
                SwitchRow(stringResource(R.string.settings_rise), rise, note = stringResource(R.string.settings_rise_note)) {
                    s.rise = it
                    v++
                }
            }
            item(key = "glance") {
                SwitchRow(stringResource(R.string.settings_glance), glance, note = stringResource(R.string.settings_glance_note)) {
                    s.onLockScreen = it
                    GlanceProvider.changed(context)
                    v++
                }
            }
            item(key = "call") {
                TextMMD(text = stringResource(R.string.settings_call_note), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 16.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (pickSnooze) {
        OptionsDialog(
            title = stringResource(R.string.settings_snooze),
            options = SNOOZES.map { it to Times.minutes(context, it) },
            chosen = snooze,
            onPick = {
                s.snoozeMinutes = it
                v++
            },
            onDismiss = { pickSnooze = false },
        )
    }
    if (pickRing) {
        OptionsDialog(
            title = stringResource(R.string.settings_ring_for),
            options = listOf(1, 5, 10, 15, 30).map { it to Times.minutes(context, it) },
            chosen = ring,
            onPick = {
                s.ringMinutes = it
                v++
            },
            onDismiss = { pickRing = false },
        )
    }
}
