package com.wanderwildwood.tokei

import android.content.Context
import android.text.format.DateFormat
import com.wanderwildwood.tokei.data.Plan
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Times, days and lengths, written the way the phone writes them: its 12- or 24-hour clock, its language. */
object Times {

    private fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    fun is24(context: Context): Boolean = DateFormat.is24HourFormat(context)

    private fun clock(context: Context): DateTimeFormatter {
        val skeleton = if (is24(context)) "Hm" else "hm"
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(context), skeleton), locale(context))
    }

    fun time(context: Context, at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clock(context).format(Instant.ofEpochMilli(at).atZone(zone))

    fun time(context: Context, t: LocalTime): String = clock(context).format(t)

    /**
     * A time in two parts, the way the stock Alarm app draws it: the figures large and AM or PM
     * small beside them. The second part is empty on the 24-hour clock.
     */
    fun split(context: Context, t: LocalTime): Pair<String, String> {
        if (is24(context)) return String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute) to ""
        val h = (t.hour + 11) % 12 + 1
        val ampm = DateFormat.getBestDateTimePattern(locale(context), "a").let {
            DateTimeFormatter.ofPattern(it, locale(context)).format(t)
        }
        return String.format(Locale.ROOT, "%d:%02d", h, t.minute) to ampm
    }

    fun split(context: Context, at: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> =
        split(context, Instant.ofEpochMilli(at).atZone(zone).toLocalTime())

    /** "Mon 5 Oct", or with the year when it is not this year's. */
    fun day(context: Context, date: LocalDate): String {
        val skeleton = if (date.year == LocalDate.now().year) "EEEdMMM" else "EEEdMMMyyyy"
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(context), skeleton), locale(context)).format(date)
    }

    fun date(at: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    /** "Today", "Yesterday", "Tomorrow", or the day itself. */
    fun relativeDay(context: Context, date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> context.getString(R.string.today)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        else -> day(context, date)
    }

    /** A time, with its day in front when that is not today: "08:00", "Yesterday 20:00". */
    fun whenShort(context: Context, at: Long): String {
        val d = date(at)
        return if (d == LocalDate.now()) time(context, at) else relativeDay(context, d) + " " + time(context, at)
    }

    fun dayName(context: Context, d: DayOfWeek, short: Boolean): String =
        d.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, locale(context))

    /** "Every day", "Weekdays", "Weekends", "Mon, Wed, Fri", or "Once" for an alarm that does not repeat. */
    fun days(context: Context, days: Int): String = when (days) {
        0 -> context.getString(R.string.repeat_once)
        Plan.EVERY_DAY -> context.getString(R.string.repeat_every_day)
        Plan.WEEKDAYS -> context.getString(R.string.repeat_weekdays)
        Plan.WEEKEND -> context.getString(R.string.repeat_weekend)
        else -> DayOfWeek.values().filter { days and Plan.bit(it) != 0 }.joinToString(", ") { dayName(context, it, true) }
    }

    /** "7 h 12 min", "12 min", "less than a minute": how long until something. */
    fun until(context: Context, ms: Long): String {
        val minutes = (ms + 59_999L) / 60_000L
        if (minutes < 1) return context.getString(R.string.less_than_a_minute)
        val d = minutes / (24 * 60)
        val h = minutes % (24 * 60) / 60
        val m = minutes % 60
        val parts = buildList {
            if (d > 0) add(context.resources.getQuantityString(R.plurals.days_short, d.toInt(), d.toInt()))
            if (h > 0) add(context.getString(R.string.hours_short, h.toInt()))
            if (m > 0 && d == 0L) add(context.getString(R.string.minutes_short, m.toInt()))
        }
        return parts.joinToString(" ")
    }

    /** A countdown or a stopwatch: "4:05", "1:02:03", with hundredths when [fine]: "4:05.27". */
    fun clock(ms: Long, fine: Boolean = false, roundUp: Boolean = false): String {
        val whole = if (roundUp) (ms + 999L) / 1_000L else ms / 1_000L
        val h = whole / 3600
        val m = whole % 3600 / 60
        val s = whole % 60
        val base = if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
        return if (fine) base + String.format(Locale.ROOT, ".%02d", ms % 1_000L / 10) else base
    }

    /** A timer's length in words: "5 min", "1 h 30 min", "45 s". */
    fun length(context: Context, seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add(context.getString(R.string.hours_short, h))
            if (m > 0) add(context.getString(R.string.minutes_short, m))
            if (s > 0) add(context.getString(R.string.seconds_short, s))
        }.ifEmpty { listOf(context.getString(R.string.seconds_short, 0)) }.joinToString(" ")
    }

    fun minutes(context: Context, n: Int): String =
        if (n == 60) context.getString(R.string.one_hour) else context.resources.getQuantityString(R.plurals.minutes, n, n)
}
