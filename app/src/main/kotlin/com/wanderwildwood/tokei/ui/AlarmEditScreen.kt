package com.wanderwildwood.tokei.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.alarm.Engine
import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.Groups
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Store
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZoneId

/**
 * Making or changing an alarm, as the stock Alarm app lays it out: a cross to leave without
 * saving, the bin and the Save (Create for a new one) in the top bar, the time in its boxes, and
 * a row for each of Repeat, Name, Group and Sound. A repeating alarm has one more row, to skip its next time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(id: Long, preset: Pair<Int, Int>?, onDone: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val existing = remember(id) { if (id == NEW) null else Store.get(context).alarm(id) }
    val start = existing ?: run {
        val t = preset?.let { LocalTime.of(it.first, it.second) } ?: LocalTime.now().plusHours(1).withMinute(0)
        Alarm(hour = t.hour, minute = t.minute)
    }
    var hour by rememberSaveable { mutableStateOf(start.hour) }
    var minute by rememberSaveable { mutableStateOf(start.minute) }
    var days by rememberSaveable { mutableStateOf(start.days) }
    var label by rememberSaveable { mutableStateOf(start.label) }
    var group by rememberSaveable { mutableStateOf(start.group) }
    val groups = remember { Groups.names(Store.get(context).alarms()) }
    var sound by rememberSaveable { mutableStateOf(start.sound) }
    var soundTitle by rememberSaveable { mutableStateOf(start.soundTitle) }
    var vibrate by rememberSaveable { mutableStateOf(start.vibrate) }
    var skip by rememberSaveable { mutableStateOf(existing?.skip?.let { it > 0 } ?: false) }
    var page by rememberSaveable { mutableStateOf("") }
    var arming by remember { mutableStateOf(false) }
    LaunchedEffect(arming) {
        if (arming) {
            delay(4_000L)
            arming = false
        }
    }

    when (page) {
        "repeat" -> {
            RepeatPage(days, { days = it }, onBack = { page = "" })
            return
        }
        "name" -> {
            NamePage(stringResource(R.string.name_title), label, { label = it }, onBack = { page = "" })
            return
        }
        "group" -> {
            GroupPage(group, groups, { group = it }, onBack = { page = "" })
            return
        }
        "sound" -> {
            SoundPage(sound, soundTitle, vibrate, { s, t -> sound = s; soundTitle = t }, { vibrate = it }, onBack = { page = "" })
            return
        }
    }

    BackHandler { onDone() }
    fun save() {
        val a = (existing ?: Alarm(hour = hour, minute = minute)).copy(
            hour = hour, minute = minute, days = days, label = label.trim(), group = Groups.clean(group, groups), sound = sound, soundTitle = soundTitle, vibrate = vibrate,
        )
        val saved = Engine.save(context, a)
        if (skip && days != 0) Engine.skipNext(context, saved, true)
        Store.get(context).alarm(saved)?.let { s -> Plan.nextRing(s, zone)?.let { toastIn(context, it) } }
        onDone()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(if (existing == null) R.string.alarm_new else R.string.alarm_edit)) },
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
                                        Engine.delete(context, existing.id)
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
                    if (!arming) BarPill(stringResource(if (existing == null) R.string.create else R.string.save)) { save() }
                },
            )
        },
    ) { padding ->
        PagedColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            item(key = "time") {
                Spacer(Modifier.height(16.dp))
                TimeStepper(hour, minute) { h, m ->
                    hour = h
                    minute = m
                }
                Spacer(Modifier.height(20.dp))
            }
            item(key = "repeat") {
                NavRow(stringResource(R.string.repeat_title), Times.days(context, days)) { page = "repeat" }
                Dotted()
            }
            item(key = "name") {
                NavRow(stringResource(R.string.name_title), label.ifBlank { stringResource(R.string.alarm) }) { page = "name" }
                Dotted()
            }
            item(key = "group") {
                NavRow(stringResource(R.string.group_title), group.trim().ifBlank { stringResource(R.string.group_none) }) { page = "group" }
                Dotted()
            }
            item(key = "sound") {
                NavRow(stringResource(R.string.sound_title), soundValue(sound, soundTitle, vibrate)) { page = "sound" }
            }
            if (days != 0) {
                item(key = "skip") {
                    Dotted()
                    val probe = Alarm(hour = hour, minute = minute, days = days, from = System.currentTimeMillis())
                    val next = Plan.skippable(probe, zone)
                    SwitchRow(
                        stringResource(R.string.skip_next),
                        skip,
                        note = next?.let { stringResource(R.string.skip_next_note, Times.whenShort(context, it)) },
                    ) { skip = it }
                }
            }
        }
    }
}
