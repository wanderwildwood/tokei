package com.wanderwildwood.tokei.ui

import android.media.AudioAttributes
import android.media.Ringtone
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.wanderwildwood.tokei.alarm.Sounds
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.Groups
import com.wanderwildwood.tokei.data.Plan
import kotlinx.coroutines.delay
import java.time.DayOfWeek

/*
 * The pages an alarm's (or a timer's) settings open onto, as the stock Alarm app has them: a back
 * arrow, the page's name, and the choices under it. Each change is kept as it is made; the alarm
 * itself is only saved by the Save in its own top bar.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    BackHandler { onBack() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = title) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        PagedColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp), content = content)
    }
}

/** The days an alarm rings on: Every day, then each day, Monday first. None ticked rings once. */
@Composable
fun RepeatPage(days: Int, onDays: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    SubPage(stringResource(R.string.repeat_title), onBack) {
        item(key = "every") {
            SwitchRow(stringResource(R.string.repeat_every_day), days == Plan.EVERY_DAY) { onDays(if (it) Plan.EVERY_DAY else 0) }
        }
        for (d in DayOfWeek.values()) {
            item(key = d.name) {
                CheckRow(com.wanderwildwood.tokei.Times.dayName(context, d, short = false).replaceFirstChar { it.uppercase() }, days and Plan.bit(d) != 0) { on ->
                    onDays(if (on) days or Plan.bit(d) else days and Plan.bit(d).inv())
                }
                Dotted(Modifier.padding(start = 40.dp))
            }
        }
        item(key = "note") {
            TextMMD(text = stringResource(R.string.repeat_none_note), style = helperStyle(), modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** The name, typed on a page of its own. */
@Composable
fun NamePage(title: String, name: String, onName: (String) -> Unit, onBack: () -> Unit) {
    // Ready to type, as the stock app's name page is.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SubPage(title, onBack) {
        item(key = "field") {
            TextFieldMMD(
                value = name,
                onValueChange = { onName(it.take(60)) },
                singleLine = true,
                placeholder = { TextMMD(text = stringResource(R.string.name_hint), style = MaterialTheme.typography.bodyMedium) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus).textActions(),
            )
        }
    }
}

/**
 * The group an alarm is in: typed, or one of those already in use pressed, or None. Typing a
 * name already in use, whatever its capitals, puts the alarm in that group.
 */
@Composable
fun GroupPage(group: String, names: List<String>, onGroup: (String) -> Unit, onBack: () -> Unit) {
    // Ready to type when there is no group yet to choose from.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (names.isEmpty()) runCatching { focus.requestFocus() } }
    SubPage(stringResource(R.string.group_title), onBack) {
        item(key = "field") {
            TextFieldMMD(
                value = group,
                onValueChange = { onGroup(it.take(Groups.MAX_NAME)) },
                singleLine = true,
                placeholder = { TextMMD(text = stringResource(R.string.group_hint), style = MaterialTheme.typography.bodyMedium) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus).textActions(),
            )
        }
        item(key = "none") {
            Spacer(Modifier.height(12.dp))
            ChoiceRow(stringResource(R.string.group_none), group.isBlank()) { onGroup(""); onBack() }
        }
        for (name in names) {
            item(key = "g:$name") {
                ChoiceRow(name, group.trim().equals(name, ignoreCase = true)) { onGroup(name); onBack() }
            }
        }
        item(key = "note") {
            TextMMD(text = stringResource(R.string.group_note), style = helperStyle(), modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/**
 * Vibration, and the sound: none, the phone's own alarm sound, one of its other alarm sounds, or
 * a sound file chosen from the phone. A sound pressed plays a few seconds of itself.
 */
@Composable
fun SoundPage(sound: String, soundTitle: String, vibrate: Boolean, onSound: (String, String) -> Unit, onVibrate: (Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val sounds = remember { Sounds.list(context) }
    var playing by remember { mutableStateOf<Ringtone?>(null) }
    fun preview(uri: Uri?) {
        playing?.stop()
        playing = uri?.let { u ->
            RingtoneManager.getRingtone(context, u)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                play()
            }
        }
    }
    LaunchedEffect(playing) {
        if (playing != null) {
            delay(5_000L)
            playing?.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { playing?.stop() } }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment.orEmpty()
            onSound(uri.toString(), name.substringBeforeLast('.'))
            preview(uri)
        }
    }

    SubPage(stringResource(R.string.sound_title), { playing?.stop(); onBack() }) {
        item(key = "vibrate") { SwitchRow(stringResource(R.string.sound_vibration), vibrate, onChange = onVibrate) }
        item(key = "heading") {
            Heading(stringResource(R.string.sound_heading))
            Dotted()
        }
        item(key = "silent") {
            SoundRow(stringResource(R.string.sound_silent), sound == Alarm.SILENT) {
                onSound(Alarm.SILENT, "")
                preview(null)
            }
        }
        item(key = "default") {
            SoundRow(stringResource(R.string.sound_default), sound.isBlank()) {
                onSound("", "")
                preview(RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM))
            }
        }
        val isFile = sound.isNotBlank() && sound != Alarm.SILENT && sounds.none { it.second.toString() == sound }
        if (isFile) {
            item(key = "file") { SoundRow(soundTitle.ifBlank { stringResource(R.string.sound_file_chosen) }, true) { preview(Uri.parse(sound)) } }
        }
        sounds.forEachIndexed { i, (title, uri) ->
            item(key = "s$i") {
                SoundRow(title, sound == uri.toString()) {
                    onSound(uri.toString(), title)
                    preview(uri)
                }
            }
        }
        item(key = "pick") {
            NavRow(stringResource(R.string.sound_pick_file), null) { pickFile.launch(arrayOf("audio/*")) }
        }
    }
}

@Composable
private fun SoundRow(title: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButtonMMD(selected = chosen, onClick = null)
        Spacer(Modifier.width(14.dp))
        TextMMD(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
        )
    }
    Box(Modifier.padding(start = 40.dp)) { Dotted() }
}

/** The name of the sound alone. */
@Composable
fun soundName(sound: String, soundTitle: String): String = when {
    sound == Alarm.SILENT -> stringResource(R.string.sound_silent)
    sound.isBlank() -> stringResource(R.string.sound_default)
    else -> soundTitle.ifBlank { stringResource(R.string.sound_file_chosen) }
}

/** What an alarm's sound row says it is set to. */
@Composable
fun soundValue(sound: String, soundTitle: String, vibrate: Boolean): String {
    val name = soundName(sound, soundTitle)
    return if (vibrate) stringResource(R.string.sound_and_vibration, name) else name
}

@Composable
fun BoxSpacer() = Spacer(Modifier.size(1.dp))
