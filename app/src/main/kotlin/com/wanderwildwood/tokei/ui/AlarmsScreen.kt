package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.mudita.mmd.components.lazy.LazyColumnMMD
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
 * its switch. Above them, only when there is something to say: what would stop an alarm ringing,
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

    Box(Modifier.fillMaxSize()) {
        LazyColumnMMD(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
            alarms.forEachIndexed { i, a ->
                item(key = a.id) {
                    AlarmRow(a, zone, onClick = { onEdit(a.id) })
                    if (i < alarms.size - 1) Dotted()
                }
            }
            item(key = "foot") { Spacer(Modifier.height(110.dp)) }
        }
        if (alarms.isNotEmpty()) {
            AddButton(stringResource(R.string.alarm_create), { onEdit(NEW) }, Modifier.align(Alignment.BottomEnd).padding(18.dp))
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
            .padding(vertical = 10.dp),
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
            } else if (a.skip > 0 && a.enabled) {
                TextMMD(
                    text = stringResource(R.string.skipping, Times.whenShort(context, a.skip)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SwitchMMD(checked = a.enabled, onCheckedChange = { on ->
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
            TextMMD(text = stringResource(R.string.missed_more, total - lines.size), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        ButtonMMD(
            onClick = {
                Store.get(context).clearMissed()
                com.wanderwildwood.tokei.data.Settings(context).stoppedAt = 0
                Notifier.cancelMissed(context)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.setup_understood), style = MaterialTheme.typography.bodySmall) }
    }
}
