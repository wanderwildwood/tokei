package com.wanderwildwood.tokei.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.alarm.Notifier
import android.os.PowerManager
import com.wanderwildwood.tokei.data.DuraSpeed
import com.wanderwildwood.tokei.data.Settings

/** Something standing between an alarm and the person it is for, and what to do about it. */
data class Problem(val text: String, val button: String, val action: () -> Unit, val done: String? = null, val onDone: (() -> Unit)? = null)

/**
 * What would stop an alarm ringing, or make it ring late, read fresh each time a screen comes
 * back: only what is wrong is shown, and when nothing is, nothing is. Medicine's list, with the
 * phone's battery saving added: on the forum, alarms that rang minutes late overnight were mended
 * by DuraSpeed and this together.
 */
fun problems(context: Context, settings: Settings, onChange: () -> Unit): List<Problem> {
    val out = mutableListOf<Problem>()
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, context.getString(R.string.setup_cannot_open), Toast.LENGTH_LONG).show() }
    }

    if (DuraSpeed.atRisk(context, settings)) {
        out += Problem(
            text = context.getString(R.string.setup_duraspeed),
            button = context.getString(R.string.setup_duraspeed_open),
            action = { open(DuraSpeed.appInfo()) },
            done = context.getString(R.string.setup_duraspeed_done),
            onDone = {
                settings.duraSpeedAllowed = true
                onChange()
            },
        )
    }
    if (!notificationsOn(context)) {
        out += Problem(
            text = context.getString(R.string.setup_notifications),
            button = context.getString(R.string.setup_turn_on),
            action = {
                open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
            },
        )
    }
    if (!context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
        out += Problem(
            text = context.getString(R.string.setup_exact),
            button = context.getString(R.string.setup_allow),
            action = {
                open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
            },
        )
    }
    // Android 14 and later let a sideloaded app take the screen only once it is allowed to:
    // without that, an alarm or a timer rings with nothing but a notification.
    if (Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
        out += Problem(
            text = context.getString(R.string.setup_full_screen),
            button = context.getString(R.string.setup_full_screen_allow),
            action = {
                open(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName)))
            },
        )
    }
    val power = context.getSystemService(PowerManager::class.java)
    if (!power.isIgnoringBatteryOptimizations(context.packageName)) {
        out += Problem(
            text = context.getString(R.string.setup_battery),
            button = context.getString(R.string.setup_battery_allow),
            action = {
                runCatching {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { open(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            },
        )
    }
    // When what the stop cost is already listed as missed, that list says it; this would say it twice.
    val stopped = settings.stoppedAt
    if (stopped > 0 && com.wanderwildwood.tokei.data.Store.get(context).missed().none { it.why == com.wanderwildwood.tokei.data.Why.STOPPED }) {
        out += Problem(
            text = context.getString(R.string.setup_stopped, Times.whenShort(context, stopped)),
            button = context.getString(R.string.setup_understood),
            action = {
                settings.stoppedAt = 0
                onChange()
            },
        )
    }
    return out
}

private fun notificationsOn(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    Notifier.channels(context)
    val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(Notifier.RINGING)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

/** One problem, in a box: what is wrong, and the press that mends it. */
@Composable
fun ProblemCard(p: Problem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        TextMMD(text = p.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        // One under the other, full width: side by side, "Open DuraSpeed" broke over two lines.
        ButtonMMD(onClick = p.action, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            TextMMD(text = p.button, style = smallBodyStyle())
        }
        if (p.done != null && p.onDone != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButtonMMD(onClick = p.onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                TextMMD(text = p.done, style = smallBodyStyle())
            }
        }
    }
}

/** The problems, read again whenever [check] changes: on coming back to the screen, and after a press. */
@Composable
fun rememberProblems(check: Int, onChange: () -> Unit): List<Problem> {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    return remember(check) { problems(context, settings, onChange) }
}
