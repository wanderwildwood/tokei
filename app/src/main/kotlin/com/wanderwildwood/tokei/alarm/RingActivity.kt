package com.wanderwildwood.tokei.alarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Ring
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.data.Store
import com.wanderwildwood.tokei.ui.monochrome

/**
 * The ringing screen, over the lock screen, laid out as the stock Alarm app's: the time large,
 * the name under it, a round Snooze in the middle and Stop at the foot. Under the round Snooze,
 * the other lengths, each of which snoozes for that long at a press. A timer has a minute more
 * where Snooze is. When more than one thing rings, the newest is shown, and the next after it.
 */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    Ringing(onDone = ::finish)
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, RingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        /**
         * Brings the screen up straight away. With the phone locked or asleep the notification's
         * full-screen intent does it anyway; this is for a phone in use, where Android would
         * otherwise show only a notification at the top.
         */
        fun show(context: Context) {
            try {
                context.startActivity(intent(context))
            } catch (e: Exception) {
                Log.w("tokei", "ringing screen not started directly: ${e.message}")
            }
        }
    }
}

@Composable
private fun Ringing(onDone: () -> Unit) {
    val context = LocalContext.current
    val version by Store.version.collectAsState()
    val settings = remember { Settings(context) }
    val ringing = remember(version) { settings.ringing }
    val top = ringing.lastOrNull()
    LaunchedEffect(top == null) { if (top == null) onDone() }
    // Back does not answer an alarm: it leaves it ringing, with this screen up.
    BackHandler { }
    if (top == null) return

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (top.timer) TimerTop(top) else AlarmTop(top)
        Spacer(Modifier.weight(1f))
        if (top.timer) {
            BigRound(stringResource(R.string.ring_add_minute), null) {
                Engine.timerAction(context, top.id) { t, now -> Plan.addMinute(t, now) }
            }
        } else {
            val n = settings.snoozeMinutes
            BigRound(stringResource(R.string.ring_snooze), Times.minutes(context, n)) { Engine.snooze(context, top.id, n) }
            Spacer(Modifier.height(16.dp))
            TextMMD(text = stringResource(R.string.ring_snooze_other), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in SNOOZES.filter { it != n }) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(10.dp))
                            .clickable { Engine.snooze(context, top.id, m) }
                            .semantics { contentDescription = context.getString(R.string.ring_snooze_for, Times.minutes(context, m)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        TextMMD(text = shortMinutes(context, m), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (ringing.size > 1) {
            TextMMD(
                text = context.resources.getQuantityString(R.plurals.ring_more, ringing.size - 1, ringing.size - 1),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(8.dp))
        }
        ButtonMMD(
            onClick = {
                if (top.timer) Engine.stopTimer(context, top.id) else Engine.dismiss(context, top.id)
            },
            modifier = Modifier.width(220.dp).height(64.dp),
        ) {
            TextMMD(text = stringResource(R.string.ring_stop), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Snooze lengths offered on the ringing screen, in minutes. */
val SNOOZES = listOf(5, 10, 15, 30, 60)

private fun shortMinutes(context: Context, m: Int): String =
    if (m == 60) context.getString(R.string.one_hour_short) else context.getString(R.string.minutes_short, m)

@Composable
private fun AlarmTop(r: Ring) {
    val context = LocalContext.current
    val alarm = remember(r) { Store.get(context).alarm(r.id) }
    val (time, ampm) = Times.split(context, r.due)
    Spacer(Modifier.height(24.dp))
    BigTime(time, ampm)
    TextMMD(
        text = alarm?.label?.ifBlank { null } ?: stringResource(R.string.alarm),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TimerTop(r: Ring) {
    val context = LocalContext.current
    val timer = remember(r) { Store.get(context).timer(r.id) }
    Spacer(Modifier.height(24.dp))
    TextMMD(text = stringResource(R.string.ring_timer_done), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    TextMMD(
        text = timer?.let { Engine.timerLabel(context, it) }.orEmpty(),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
}

/** The time as the stock Alarm app draws it on this screen: large figures, AM or PM small at the top right. */
@Composable
fun BigTime(time: String, ampm: String, size: Int = 80) {
    Row(verticalAlignment = Alignment.Top) {
        TextMMD(text = time, fontSize = size.sp, lineHeight = (size * 1.1).sp, fontWeight = FontWeight.Normal)
        if (ampm.isNotEmpty()) {
            TextMMD(text = ampm, fontSize = (size * 0.3).sp, modifier = Modifier.padding(top = (size * 0.12).dp))
        }
    }
}

/** The round button in the middle of the ringing screen. */
@Composable
private fun BigRound(title: String, sub: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(190.dp)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextMMD(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (sub != null) TextMMD(text = sub, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
