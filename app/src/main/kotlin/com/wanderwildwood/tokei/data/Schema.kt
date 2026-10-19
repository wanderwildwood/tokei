package com.wanderwildwood.tokei.data

/**
 * The tables of [Store]'s file, and what brings an older file up to date. Plain statements with
 * nothing of Android in them, so a test can run them in SQLite on the computer.
 */
object Schema {
    const val VERSION = 3

    /** The tables as a new install makes them. */
    val CREATE = listOf(
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
            skip INTEGER NOT NULL DEFAULT 0,
            grp TEXT NOT NULL DEFAULT ''
        )""",
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
            vibrate INTEGER NOT NULL DEFAULT 1,
            kept INTEGER NOT NULL DEFAULT 1
        )""",
        """CREATE TABLE missed (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timer INTEGER NOT NULL,
            label TEXT NOT NULL DEFAULT '',
            due INTEGER NOT NULL,
            why TEXT NOT NULL
        )""",
    )

    /** What brings a file made by version [from] of the tables up to [VERSION]. */
    fun upgrade(from: Int): List<String> = buildList {
        // 2: timers started on the fly. Every timer from before was made to be kept.
        if (from < 2) add("ALTER TABLE timer ADD COLUMN kept INTEGER NOT NULL DEFAULT 1")
        // 3: groups of alarms. Every alarm from before is in none.
        if (from < 3) add("ALTER TABLE alarm ADD COLUMN grp TEXT NOT NULL DEFAULT ''")
    }
}
