package com.wanderwildwood.tokei.data

import android.content.Context

/** The few choices the app keeps, and the small state that is not a list, in its own preferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** How long the big Snooze button, and Snooze on the notification, put an alarm off. */
    var snoozeMinutes: Int
        get() = prefs.getInt("snooze", 10)
        set(v) = prefs.edit().putInt("snooze", v).apply()

    /** How long an alarm or a timer rings without an answer before it stops and is written down as missed. */
    var ringMinutes: Int
        get() = prefs.getInt("ring_minutes", 10)
        set(v) = prefs.edit().putInt("ring_minutes", v).apply()

    /** Start quiet and grow to full volume over [RISE_MS], rather than starting at full. */
    var rise: Boolean
        get() = prefs.getBoolean("rise", false)
        set(v) = prefs.edit().putBoolean("rise", v).apply()

    /** The next alarm on Glance's lock-screen panel. Off until asked for. */
    var onLockScreen: Boolean
        get() = prefs.getBoolean("glance", false)
        set(v) = prefs.edit().putBoolean("glance", v).apply()

    /** A line of weather from Sky on the ringing screen of an alarm. Off until asked for. */
    var skyOnRing: Boolean
        get() = prefs.getBoolean("sky_on_ring", false)
        set(v) = prefs.edit().putBoolean("sky_on_ring", v).apply()

    /** A short tick at each key press while a time or a length is set. Off until asked for; it is the alarm's own vibration that has a switch of its own. */
    var buzz: Boolean
        get() = prefs.getBoolean(BUZZ, BUZZ_DEFAULT)
        set(v) = prefs.edit().putBoolean(BUZZ, v).apply()

    /** The person said Clock is switched on in DuraSpeed's list. */
    var duraSpeedAllowed: Boolean
        get() = prefs.getBoolean("duraspeed_allowed", false)
        set(v) = prefs.edit().putBoolean("duraspeed_allowed", v).apply()

    /** The newest stop by the system already accounted for (DuraSpeed.kt). */
    var stopSeen: Long
        get() = prefs.getLong("stop_seen", 0L)
        set(v) = prefs.edit().putLong("stop_seen", v).apply()

    /** When the app was last found stopped by the system, for the notice on the first screen; 0 for none to show. */
    var stoppedAt: Long
        get() = prefs.getLong("stopped_at", 0L)
        set(v) = prefs.edit().putLong("stopped_at", v).apply()

    /** The world clock's cities, as time-zone names, in the order they were added. */
    var cities: List<String>
        get() = prefs.getString("cities", "").orEmpty().split(',').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("cities", v.joinToString(",")).apply()

    var stopwatch: Stopwatch
        get() = Stopwatch.decode(prefs.getString("stopwatch", "").orEmpty())
        set(v) = prefs.edit().putString("stopwatch", v.encode()).apply()

    /** What is ringing now, oldest first. Written at once, because the ringing screen reads it straight after. */
    var ringing: List<Ring>
        get() = Ring.decodeAll(prefs.getString("ringing", "").orEmpty())
        set(v) {
            prefs.edit().putString("ringing", Ring.encodeAll(v)).commit()
        }

    /** When the newest thing started ringing: the ringing stops [ringMinutes] after it. */
    var ringSince: Long
        get() = prefs.getLong("ring_since", 0L)
        set(v) = prefs.edit().putLong("ring_since", v).apply()

    companion object {
        const val RISE_MS = 60_000L
        const val BUZZ = "buzz"
        const val BUZZ_DEFAULT = false
    }
}
