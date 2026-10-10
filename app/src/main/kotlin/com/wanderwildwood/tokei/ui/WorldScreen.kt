package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.data.Cities
import com.wanderwildwood.tokei.data.Settings
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * The world clock: the time here first, then each chosen city with its time, its day beside this
 * one's, and how far ahead or behind it is today. It redraws on the minute.
 */
@Composable
fun WorldScreen(onAdd: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var cities by remember { mutableStateOf(settings.cities) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    LifecycleResumeEffect(Unit) {
        cities = settings.cities
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    var removing by remember { mutableStateOf<String?>(null) }
    val here = ZoneId.systemDefault()

    Box(Modifier.fillMaxSize()) {
        com.mudita.mmd.components.lazy.LazyColumnMMD(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item(key = "here") {
                CityRow(stringResource(R.string.world_here), here, here, now, null, sub = Times.day(context, Times.date(now)))
                Dotted()
            }
            if (cities.isEmpty()) {
                item(key = "empty") { Empty(stringResource(R.string.world_empty), stringResource(R.string.world_add)) { onAdd() } }
            }
            cities.forEachIndexed { i, z ->
                item(key = z) {
                    val name = Cities.named(z)?.name ?: z.substringAfterLast('/').replace('_', ' ')
                    CityRow(name, ZoneId.of(z), here, now, onClick = { removing = z })
                    if (i < cities.size - 1) Dotted()
                }
            }
            item(key = "foot") { Spacer(Modifier.height(110.dp)) }
        }
        if (cities.isNotEmpty()) {
            AddButton(stringResource(R.string.world_add), onAdd, Modifier.align(Alignment.BottomEnd).padding(18.dp))
        }
    }

    removing?.let { z ->
        val name = Cities.named(z)?.name ?: z
        EInkDialog(onDismiss = { removing = null }) {
            TextMMD(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            ArmedRow(stringResource(R.string.world_remove), stringResource(R.string.world_remove_armed)) {
                settings.cities = settings.cities - z
                cities = settings.cities
                removing = null
            }
            DialogButton(stringResource(R.string.close)) { removing = null }
        }
    }
}

@Composable
private fun CityRow(name: String, zone: ZoneId, here: ZoneId, now: Long, onClick: (() -> Unit)?, sub: String? = null) {
    val context = LocalContext.current
    val (time, ampm) = Times.split(context, now, zone)
    Column(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            TextMMD(text = time, fontSize = 52.sp, lineHeight = 58.sp)
            if (ampm.isNotEmpty()) TextMMD(text = ampm, fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        }
        TextMMD(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextMMD(text = sub ?: dayAndOffset(context, zone, here, now), style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Tomorrow, 13 h ahead", "Today, same time", "Sat 10 Oct, 4 h 30 min behind". */
fun dayAndOffset(context: android.content.Context, zone: ZoneId, here: ZoneId, now: Long): String {
    val instant = Instant.ofEpochMilli(now)
    val theirs = instant.atZone(zone)
    val ours = instant.atZone(here)
    val day = Times.relativeDay(context, theirs.toLocalDate(), ours.toLocalDate())
    val diff = theirs.offset.totalSeconds - ours.offset.totalSeconds
    if (diff == 0) return context.getString(R.string.world_same, day)
    val h = abs(diff) / 3600
    val m = abs(diff) % 3600 / 60
    val amount = if (m == 0) context.getString(R.string.hours_short, h) else context.getString(R.string.hours_minutes_short, h, m)
    return context.getString(if (diff > 0) R.string.world_ahead else R.string.world_behind, day, amount)
}

/** Adding a city: a field to narrow the list, then the cities west to east. */
@Composable
fun CityPicker(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var query by remember { mutableStateOf("") }
    val now = remember { System.currentTimeMillis() }
    val here = ZoneId.systemDefault()
    val chosen = remember { settings.cities.toSet() }
    val sorted = remember {
        Cities.all.sortedBy { ZoneId.of(it.zone).rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds }
    }
    val shown = sorted.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    SubPage(stringResource(R.string.world_add), onDone) {
        item(key = "search") {
            TextFieldMMD(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { TextMMD(text = stringResource(R.string.world_search), style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).textActions(),
            )
        }
        for (c in shown) {
            item(key = c.zone) {
                val added = c.zone in chosen
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !added) {
                            settings.cities = settings.cities + c.zone
                            onDone()
                        }
                        .padding(vertical = 10.dp),
                ) {
                    TextMMD(text = c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextMMD(
                        text = if (added) stringResource(R.string.world_already) else Times.time(context, now, ZoneId.of(c.zone)) + ", " + dayAndOffset(context, ZoneId.of(c.zone), here, now),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Dotted()
            }
        }
        if (shown.isEmpty()) {
            item(key = "none") { TextMMD(text = stringResource(R.string.world_none_found), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}
