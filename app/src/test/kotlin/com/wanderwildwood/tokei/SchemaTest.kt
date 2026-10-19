package com.wanderwildwood.tokei

import com.wanderwildwood.tokei.data.Schema
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/** The database upgrade, run in SQLite on the computer against files as older versions made them. */
class SchemaTest {

    private fun db(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    private fun Connection.run(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.columns(table: String): List<String> =
        createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($table)").use { r -> buildList { while (r.next()) add(r.getString("name")) } }
        }

    /** The alarm table as 0.1.0 to 0.1.6 made it (versions 1 and 2 of the file). */
    private val alarmV2 = """CREATE TABLE alarm (
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
    )"""

    private val timerV1 = """CREATE TABLE timer (
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
    )"""

    private val missed = """CREATE TABLE missed (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        timer INTEGER NOT NULL,
        label TEXT NOT NULL DEFAULT '',
        due INTEGER NOT NULL,
        why TEXT NOT NULL
    )"""

    private fun old(version: Int): Connection = db().apply {
        run(alarmV2)
        run(timerV1)
        if (version >= 2) run("ALTER TABLE timer ADD COLUMN kept INTEGER NOT NULL DEFAULT 1")
        run(missed)
        run("INSERT INTO alarm (hour, minute, days, label, enabled, skip) VALUES (6, 30, 31, 'Wake up', 1, 0)")
        run("INSERT INTO alarm (hour, minute, days, label, enabled, skip) VALUES (21, 5, 0, '', 0, 0)")
    }

    private fun fresh(): Connection = db().apply { Schema.CREATE.forEach { run(it) } }

    @Test fun fromVersionTwoAlarmsKeepEverythingAndAreInNoGroup() {
        val c = old(2)
        Schema.upgrade(2).forEach { c.run(it) }
        c.createStatement().use { st ->
            st.executeQuery("SELECT hour, minute, days, label, enabled, grp FROM alarm ORDER BY id").use { r ->
                r.next()
                assertEquals(6, r.getInt("hour")); assertEquals(30, r.getInt("minute")); assertEquals(31, r.getInt("days"))
                assertEquals("Wake up", r.getString("label")); assertEquals(1, r.getInt("enabled")); assertEquals("", r.getString("grp"))
                r.next()
                assertEquals(21, r.getInt("hour")); assertEquals(0, r.getInt("enabled")); assertEquals("", r.getString("grp"))
            }
        }
        // A new alarm saved afterwards takes a group.
        c.run("INSERT INTO alarm (hour, minute, grp) VALUES (7, 0, 'Office')")
        c.createStatement().use { st ->
            st.executeQuery("SELECT grp FROM alarm WHERE hour = 7").use { r -> r.next(); assertEquals("Office", r.getString(1)) }
        }
    }

    @Test fun anUpgradedFileHasTheSameColumnsAsANewOne() {
        for (from in 1 until Schema.VERSION) {
            val c = old(from)
            Schema.upgrade(from).forEach { c.run(it) }
            val n = fresh()
            for (table in listOf("alarm", "timer", "missed")) assertEquals("$table from $from", n.columns(table), c.columns(table))
        }
    }

    @Test fun aCurrentFileNeedsNothing() {
        assertEquals(emptyList<String>(), Schema.upgrade(Schema.VERSION))
    }
}
