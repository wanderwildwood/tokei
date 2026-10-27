package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.tokei.data.Groups
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.alarm.Engine
import com.wanderwildwood.tokei.alarm.Notifier
import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Store
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * The alarms, as the stock Alarm app lists them: the time large, its days and name under it, and
 * its switch, with the alarms of a group together under its name. Above them, only when there is something to say: what would stop an alarm ringing,
 * what was missed, and how long until the next one rings.
 */
@Composable
fun AlarmsScreen(onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val version by Store.version.collectAsState()
    val alarms = remember(version) { Store.get(context).alarms() }
    val missed = remember(version) { Store.get(context).missed() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    var check by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        check++
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    val problems = rememberProblems(check) { check++ }
    val zone = ZoneId.systemDefault()
    val wake = remember(version, now) { Plan.nextWake(alarms, zone) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        PagedColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            problems.forEachIndexed { i, p -> item(key = "problem:$i") { ProblemCard(p) } }
            if (missed.isNotEmpty()) item(key = "missed") { MissedCard(missed.take(5).map { Notifier.line(context, it) }, missed.size) }
            if (wake != null) {
                item(key = "next") {
                    TextMMD(
                        text = stringResource(R.string.next_alarm_in, Times.until(context, wake.at - now)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
            }
            if (alarms.isEmpty()) {
                item(key = "empty") { Empty(stringResource(R.string.alarms_empty), stringResource(R.string.alarm_create)) { onEdit(NEW) } }
            }
            val sections = Groups.sections(alarms)
            sections.forEach { section ->
                section.alarms.forEachIndexed { i, a ->
                    item(key = a.id) {
                        // The heading shares its first alarm's row, so a page never ends on a
                        // heading with its alarms over the page.
                        if (i == 0 && section.name.isNotEmpty()) {
                            GroupHeading(section.name, section.alarms, zone, onMenu = { menuFor = section.name })
                        }
                        AlarmRow(a, zone, onClick = { onEdit(a.id) })
                        if (i < section.alarms.size - 1) Dotted()
                    }
                }
            }
        }
    }
    menuFor?.let { name ->
        val group = alarms.filter { it.group == name }
        if (group.isEmpty()) menuFor = null else GroupMenu(name, group, Groups.names(alarms), onDismiss = { menuFor = null })
    }
}

/**
 * Over a group's alarms: its name and one switch for all of them. Pressing the name, or holding
 * it, opens what else can be done to the group at once.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeading(name: String, group: List<Alarm>, zone: ZoneId, onMenu: () -> Unit) {
    val context = LocalContext.current
    // One line, name and count, so a group's heading and first alarm still fit under another group.
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onMenu, onLongClick = onMenu)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextMMD(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextMMD(
                text = context.resources.getQuantityString(R.plurals.group_count, group.size, group.size),
                style = helperStyle(),
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Spacer(Modifier.width(12.dp))
            SwitchMMD(checked = Groups.isOn(group), onCheckedChange = { on ->
                Engine.setEnabled(context, Groups.toSwitch(group, on), on)
                if (on) {
                    val store = Store.get(context)
                    group.mapNotNull { store.alarm(it.id)?.let { a -> Plan.nextRing(a, zone) } }.minOrNull()?.let { toastIn(context, it) }
                }
            })
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.onSurface))
    }
}

/** What a group's heading opens: skip the next time of all its repeating alarms, or rename it. */
@Composable
private fun GroupMenu(name: String, group: List<Alarm>, names: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var renaming by remember(name) { mutableStateOf(false) }
    var typed by remember(name) { mutableStateOf(name) }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        if (renaming) {
            TextFieldMMD(
                value = typed,
                onValueChange = { typed = it.take(Groups.MAX_NAME) },
                singleLine = true,
                placeholder = { TextMMD(text = stringResource(R.string.group_hint), style = MaterialTheme.typography.bodyMedium) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().textActions(),
            )
            TextMMD(text = stringResource(R.string.group_rename_note), style = helperStyle(), modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            DialogButtons(onCancel = { renaming = false }, onOk = {
                Engine.renameGroup(context, name, Groups.clean(typed, names.filter { it != name }))
                onDismiss()
            }, okLabel = stringResource(R.string.save))
        } else {
            val skippable = Groups.skippable(group)
            SwitchRow(
                stringResource(R.string.group_skip_next),
                Groups.isSkipping(group),
                note = stringResource(if (skippable.isEmpty()) R.string.group_skip_none else R.string.group_skip_note),
            ) { on ->
                if (skippable.isNotEmpty()) Engine.skipNext(context, Groups.toSkip(group, on), on)
            }
            Dotted()
            SettingRow(stringResource(R.string.group_rename), null) { renaming = true }
            Spacer(Modifier.height(8.dp))
            DialogButton(stringResource(R.string.close), onClick = onDismiss)
        }
    }
}

@Composable
private fun AlarmRow(a: Alarm, zone: ZoneId, onClick: () -> Unit) {
    val context = LocalContext.current
    val (time, ampm) = Times.split(context, a.time)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Snug enough that four alarms and the "next alarm" line fill one page.
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                TextMMD(text = time, fontSize = 56.sp, lineHeight = 62.sp)
                if (ampm.isNotEmpty()) TextMMD(text = ampm, fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
            }
            val days = Times.days(context, a.days)
            TextMMD(
                text = if (a.label.isBlank()) days else stringResource(R.string.days_and_name, days, a.label),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (a.snoozedUntil > 0) {
                TextMMD(
                    text = stringResource(R.string.snoozed_until, Times.time(context, a.snoozedUntil)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                // The alarm itself is as it was; only the snooze goes.
                TextMMD(
                    text = stringResource(R.string.snooze_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { Engine.cancelSnooze(context, a.id) }
                        .padding(vertical = 12.dp),
                )
            } else if (a.skip > 0 && a.enabled) {
                TextMMD(
                    text = stringResource(R.string.skipping, Times.whenShort(context, a.skip)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SwitchMMD(checked = a.isOn, onCheckedChange = { on ->
            Engine.setEnabled(context, a.id, on)
            if (on) {
                Store.get(context).alarm(a.id)?.let { saved ->
                    Plan.nextRing(saved, zone)?.let { toastIn(context, it) }
                }
            }
        })
    }
}

/** "Alarm in 7 h 12 min", said once when an alarm is set, as the stock app does. */
fun toastIn(context: android.content.Context, at: Long) {
    android.widget.Toast.makeText(
        context,
        context.getString(R.string.alarm_rings_in, Times.until(context, at - System.currentTimeMillis())),
        android.widget.Toast.LENGTH_SHORT,
    ).show()
}

/** What was missed, each with its reason, and the press that says it has been read. */
@Composable
private fun MissedCard(lines: List<String>, total: Int) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        TextMMD(
            text = context.resources.getQuantityString(R.plurals.missed_title, total, total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        lines.forEach { TextMMD(text = it, style = MaterialTheme.typography.bodyMedium) }
        if (total > lines.size) {
            TextMMD(text = stringResource(R.string.missed_more, total - lines.size), style = helperStyle())
        }
        Spacer(Modifier.height(12.dp))
        ButtonMMD(
            onClick = {
                Store.get(context).clearMissed()
                com.wanderwildwood.tokei.data.Settings(context).stoppedAt = 0
                Notifier.cancelMissed(context)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.setup_understood), style = smallBodyStyle()) }
    }
}
