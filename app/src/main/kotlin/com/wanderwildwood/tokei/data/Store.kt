package com.wanderwildwood.tokei.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The alarms, the timers and what was missed, in one small SQLite file in the app's own storage.
 * Fossify Clock kept its alarms in SQLite and its timers in Room; here they share one file and
 * no code generation. Nothing else reads it, and it is not backed up anywhere.
 *
 * Every write bumps [version], which is how the screens know to read again.
 */
class Store private constructor(context: Context) :
    SQLiteOpenHelper(context, "tokei.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE alarm (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hour INTEGER NOT NULL,
                minute INTEGER NOT NULL,
                days INTEGER NOT NULL DEFAULT 0,
                label TEXT NOT NULL DEFAULT '',
                sound TEXT NOT NULL DEFAULT '',
                sound_title TEXT NOT NULL DEFAULT '',
                vibrate INTEGER NOT NULL DEFAULT 1,
                enabled INTEGER NOT NULL DEFAULT 1,
                caught_up INTEGER NOT NULL DEFAULT 0,
                snoozed_until INTEGER NOT NULL DEFAULT 0,
                skip INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL(
            """CREATE TABLE timer (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seconds INTEGER NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                state INTEGER NOT NULL DEFAULT 0,
                ends_at INTEGER NOT NULL DEFAULT 0,
                left_ms INTEGER NOT NULL DEFAULT 0,
                started_at INTEGER NOT NULL DEFAULT 0,
                sound TEXT NOT NULL DEFAULT '',
                sound_title TEXT NOT NULL DEFAULT '',
                vibrate INTEGER NOT NULL DEFAULT 1
            )""",
        )
        db.execSQL(
            """CREATE TABLE missed (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timer INTEGER NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                due INTEGER NOT NULL,
                why TEXT NOT NULL
            )""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------------------------------------------------------------- alarms

    /** In the order of the clock: earliest in the day first. */
    fun alarms(): List<Alarm> =
        readableDatabase.query("alarm", null, null, null, null, null, "hour, minute, id").use { c ->
            buildList { while (c.moveToNext()) add(alarmAt(c)) }
        }

    fun alarm(id: Long): Alarm? =
        readableDatabase.query("alarm", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) alarmAt(c) else null
        }

    fun save(a: Alarm): Long {
        val v = ContentValues().apply {
            put("hour", a.hour)
            put("minute", a.minute)
            put("days", a.days)
            put("label", a.label)
            put("sound", a.sound)
            put("sound_title", a.soundTitle)
            put("vibrate", if (a.vibrate) 1 else 0)
            put("enabled", if (a.enabled) 1 else 0)
            put("caught_up", a.from)
            put("snoozed_until", a.snoozedUntil)
            put("skip", a.skip)
        }
        val id = if (a.id > 0) {
            writableDatabase.update("alarm", v, "id = ?", arrayOf(a.id.toString()))
            a.id
        } else {
            writableDatabase.insert("alarm", null, v)
        }
        bump()
        return id
    }

    fun deleteAlarm(id: Long) {
        writableDatabase.delete("alarm", "id = ?", arrayOf(id.toString()))
        bump()
    }

    private fun alarmAt(c: Cursor) = Alarm(
        id = c.long("id"),
        hour = c.int("hour"),
        minute = c.int("minute"),
        days = c.int("days"),
        label = c.str("label"),
        sound = c.str("sound"),
        soundTitle = c.str("sound_title"),
        vibrate = c.int("vibrate") != 0,
        enabled = c.int("enabled") != 0,
        from = c.long("caught_up"),
        snoozedUntil = c.long("snoozed_until"),
        skip = c.long("skip"),
    )

    // ---------------------------------------------------------------- timers

    /** In the order they were made. */
    fun timers(): List<Timer> =
        readableDatabase.query("timer", null, null, null, null, null, "id").use { c ->
            buildList { while (c.moveToNext()) add(timerAt(c)) }
        }

    fun timer(id: Long): Timer? =
        readableDatabase.query("timer", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) timerAt(c) else null
        }

    fun save(t: Timer): Long {
        val v = ContentValues().apply {
            put("seconds", t.seconds)
            put("label", t.label)
            put("state", t.state)
            put("ends_at", t.endsAt)
            put("left_ms", t.left)
            put("started_at", t.startedAt)
            put("sound", t.sound)
            put("sound_title", t.soundTitle)
            put("vibrate", if (t.vibrate) 1 else 0)
        }
        val id = if (t.id > 0) {
            writableDatabase.update("timer", v, "id = ?", arrayOf(t.id.toString()))
            t.id
        } else {
            writableDatabase.insert("timer", null, v)
        }
        bump()
        return id
    }

    fun deleteTimer(id: Long) {
        writableDatabase.delete("timer", "id = ?", arrayOf(id.toString()))
        bump()
    }

    private fun timerAt(c: Cursor) = Timer(
        id = c.long("id"),
        seconds = c.int("seconds"),
        label = c.str("label"),
        state = c.int("state"),
        endsAt = c.long("ends_at"),
        left = c.long("left_ms"),
        startedAt = c.long("started_at"),
        sound = c.str("sound"),
        soundTitle = c.str("sound_title"),
        vibrate = c.int("vibrate") != 0,
    )

    // ---------------------------------------------------------------- missed

    /** Newest first. */
    fun missed(): List<Missed> =
        readableDatabase.query("missed", null, null, null, null, null, "due DESC, id DESC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Missed(
                            id = c.long("id"),
                            timer = c.int("timer") != 0,
                            label = c.str("label"),
                            due = c.long("due"),
                            why = runCatching { Why.valueOf(c.str("why")) }.getOrDefault(Why.UNKNOWN),
                        ),
                    )
                }
            }
        }

    fun addMissed(m: Missed) {
        writableDatabase.insert(
            "missed",
            null,
            ContentValues().apply {
                put("timer", if (m.timer) 1 else 0)
                put("label", m.label)
                put("due", m.due)
                put("why", m.why.name)
            },
        )
        bump()
    }

    fun clearMissed() {
        writableDatabase.delete("missed", null, null)
        bump()
    }

    private fun Cursor.long(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.int(col: String) = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.str(col: String) = getString(getColumnIndexOrThrow(col)).orEmpty()

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }

        private val _version = MutableStateFlow(0)

        /** Goes up on every change, so a screen reading the store knows to read it again. */
        val version: StateFlow<Int> = _version

        fun bump() {
            _version.value = _version.value + 1
        }
    }
}
