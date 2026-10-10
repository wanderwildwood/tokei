package com.wanderwildwood.tokei.glance

import android.content.Context
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import com.wanderwildwood.tokei.data.Plan
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.data.Store
import com.wanderwildwood.tokei.data.Timer
import java.time.LocalDate
import java.time.ZoneId

/**
 * The lock-screen lines for Glance: the next alarm, with its day and name (a snoozed one says
 * so, in bold), then any timer running, with when it ends. Off until switched on in Settings.
 */
class NextAlarmOnLockScreen : GlanceProvider() {

    override fun enabled(context: Context): Boolean = Settings(context).onLockScreen

    override fun lines(context: Context): List<Line> {
        val store = Store.get(context)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val out = mutableListOf<Line>()

        Plan.nextWake(store.alarms(), zone)?.let { w ->
            val name = w.alarm.label.ifBlank { context.getString(R.string.alarm) }
            val day = Times.date(w.at)
            val text = if (w.snoozed) {
                context.getString(R.string.glance_snoozed, name)
            } else if (day == LocalDate.now()) {
                name
            } else {
                context.getString(R.string.glance_day_and_name, Times.relativeDay(context, day), name)
            }
            out += Line(text = text, lead = Times.time(context, w.at), bold = w.snoozed)
        }

        store.timers()
            .filter { it.state == Timer.RUNNING && it.endsAt > now }
            .sortedBy { it.endsAt }
            .take(2)
            .forEach {
                out += Line(
                    text = context.getString(R.string.glance_timer, it.label.ifBlank { Times.length(context, it.seconds) }),
                    lead = Times.time(context, it.endsAt),
                )
            }

        val heading = context.getString(R.string.app_name)
        return out.take(3).mapIndexed { i, l -> if (i == 0) l.copy(heading = heading) else l }
    }
}
