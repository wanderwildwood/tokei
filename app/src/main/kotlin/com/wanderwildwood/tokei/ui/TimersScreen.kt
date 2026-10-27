package com.wanderwildwood.tokei.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.alarm.Engine
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Store
import com.wanderwildwood.tokei.data.Timer
import kotlinx.coroutines.delay

/**
 * The timers, kept like the alarms: each stays in the list with its length and name, to start
 * again whenever it is wanted, and any number can run at once. A running one counts down a second
 * at a time; the panel redraws no more often than that.
 */
@Composable
fun TimersScreen(onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val version by Store.version.collectAsState()
    val timers = remember(version) { Store.get(context).timers() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = timers.any { it.state == Timer.RUNNING }
    LaunchedEffect(running, version) {
        now = System.currentTimeMillis()
        while (running) {
            delay(1_000L - System.currentTimeMillis() % 1_000L)
            now = System.currentTimeMillis()
        }
    }

    Box(Modifier.fillMaxSize()) {
        PagedColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (timers.isEmpty()) {
                item(key = "empty") { Empty(stringResource(R.string.timers_empty), stringResource(R.string.timer_create)) { onEdit(NEW) } }
            }
            timers.forEachIndexed { i, t ->
                item(key = t.id) {
                    TimerRow(t, now) { onEdit(t.id) }
                    if (i < timers.size - 1) Dotted()
                }
            }
        }
    }
}

@Composable
private fun TimerRow(t: Timer, now: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val left = Plan.remaining(t, now)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = Times.clock(left, roundUp = true), fontSize = 52.sp, lineHeight = 58.sp)
            val name = t.label.ifBlank { Times.length(context, t.seconds) }
            val state = when (t.state) {
                Timer.RUNNING -> stringResource(R.string.timer_ends_at, Times.time(context, t.endsAt))
                Timer.PAUSED -> stringResource(R.string.timer_paused)
                Timer.RINGING -> stringResource(R.string.ring_timer_done)
                else -> null
            }
            TextMMD(text = if (t.label.isBlank()) name else stringResource(R.string.days_and_name, Times.length(context, t.seconds), t.label), style = MaterialTheme.typography.bodyMedium)
            if (state != null) TextMMD(text = state, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            when (t.state) {
                // A timer started on the fly offers Keep in its second place; once kept, it is
                // like any other.
                Timer.RUNNING -> {
                    SmallBox(stringResource(R.string.timer_pause), filled = true) { Engine.timerAction(context, t.id) { x, n -> Plan.pause(x, n) } }
                    if (!t.kept) {
                        SmallBox(stringResource(R.string.timer_keep)) { Engine.timerAction(context, t.id) { x, _ -> x.copy(kept = true) } }
                    } else {
                        SmallBox(stringResource(R.string.ring_add_minute_short)) { Engine.timerAction(context, t.id) { x, n -> Plan.addMinute(x, n) } }
                    }
                }
                Timer.PAUSED -> {
                    SmallBox(stringResource(R.string.timer_resume), filled = true) { Engine.timerAction(context, t.id) { x, n -> Plan.start(x, n) } }
                    if (!t.kept) {
                        SmallBox(stringResource(R.string.timer_keep)) { Engine.timerAction(context, t.id) { x, _ -> x.copy(kept = true) } }
                    } else {
                        SmallBox(stringResource(R.string.timer_reset)) { Engine.timerAction(context, t.id) { x, _ -> Plan.reset(x) } }
                    }
                }
                Timer.RINGING -> SmallBox(stringResource(R.string.ring_stop), filled = true) { Engine.stopTimer(context, t.id) }
                else -> SmallBox(stringResource(R.string.timer_start), filled = true) { Engine.timerAction(context, t.id) { x, n -> Plan.start(x, n) } }
            }
        }
    }
}

/**
 * Making or changing a timer: its length in boxes, typed or stepped, and its name and sound, as
 * an alarm has them. A new one is started straight away with Start and is not kept, so a timer
 * for the tea does not stay in the list for ever; Keep in the bar puts it there without starting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerEditScreen(id: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val existing = remember(id) { if (id == NEW) null else Store.get(context).timer(id) }
    var seconds by rememberSaveable { mutableStateOf(existing?.seconds ?: 300) }
    var label by rememberSaveable { mutableStateOf(existing?.label.orEmpty()) }
    var sound by rememberSaveable { mutableStateOf(existing?.sound.orEmpty()) }
    var soundTitle by rememberSaveable { mutableStateOf(existing?.soundTitle.orEmpty()) }
    var vibrate by rememberSaveable { mutableStateOf(existing?.vibrate ?: true) }
    var page by rememberSaveable { mutableStateOf("") }
    var arming by remember { mutableStateOf(false) }
    LaunchedEffect(arming) {
        if (arming) {
            delay(ARM_MS)
            arming = false
        }
    }
    when (page) {
        "name" -> {
            NamePage(stringResource(R.string.name_title), label, { label = it }, onBack = { page = "" })
            return
        }
        "sound" -> {
            SoundPage(sound, soundTitle, vibrate, { s, t -> sound = s; soundTitle = t }, { vibrate = it }, onBack = { page = "" })
            return
        }
    }
    BackHandler { onDone() }

    fun save(start: Boolean) {
        if (seconds <= 0) return
        val base = existing ?: Timer(seconds = seconds, kept = !start)
        // A new length starts over; the same length keeps a running timer running.
        val kept = if (existing != null && existing.seconds != seconds) Plan.reset(base) else base
        val tid = Engine.saveTimer(context, kept.copy(seconds = seconds, label = label.trim(), sound = sound, soundTitle = soundTitle, vibrate = vibrate))
        if (start) Engine.timerAction(context, tid) { x, n -> Plan.start(x, n) }
        onDone()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(if (existing == null) R.string.timer_new else R.string.timer_edit)) },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.cd_close), onDone) },
                actions = {
                    if (existing != null) {
                        if (arming) {
                            TextMMD(
                                text = stringResource(R.string.delete_armed),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        Engine.deleteTimer(context, existing.id)
                                        onDone()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                            )
                        } else {
                            Box(Modifier.size(48.dp).clickable { arming = true }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Delete, stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    if (!arming) BarPill(stringResource(if (existing == null) R.string.timer_keep else R.string.save)) { save(false) }
                },
            )
        },
    ) { padding ->
        PagedColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp)) {
            item(key = "length") {
                Spacer(Modifier.height(16.dp))
                LengthStepper(seconds) { seconds = it }
                if (seconds <= 0) {
                    TextMMD(
                        text = stringResource(R.string.timer_zero),
                        style = helperStyle(),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "name") {
                Box(Modifier.padding(horizontal = 12.dp)) {
                    Column {
                        NavRow(stringResource(R.string.name_title), label.ifBlank { stringResource(R.string.name_none) }) { page = "name" }
                        Dotted()
                        NavRow(stringResource(R.string.sound_title), soundValue(sound, soundTitle, vibrate)) { page = "sound" }
                    }
                }
            }
            item(key = "start") {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    DialogButton(stringResource(if (existing == null) R.string.timer_start else R.string.timer_save_start), filled = true) { save(true) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
