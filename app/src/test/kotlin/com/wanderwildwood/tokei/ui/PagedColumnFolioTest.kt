package com.wanderwildwood.tokei.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PagedColumnFolioTest {

    @Test
    fun rowsThatFitMakeOnePage() {
        val starts = pageStarts(listOf(100, 100, 100), 400)
        assertEquals(1, starts.size)
    }

    @Test
    fun noRowsIsOnePage() {
        assertEquals(1, pageStarts(emptyList(), 400).size)
        assertEquals(1, pageStarts(listOf(50), 0).size)
    }

    @Test
    fun rowsPerPageFromMeasuredHeights() {
        // 400 px pages and 90 px rows: four whole rows a page.
        val starts = pageStarts(List(10) { 90 }, 400)
        assertEquals(listOf(0, 4, 8), starts.map { it.row })
    }

    @Test
    fun lastPartialPageCounts() {
        // Nine rows at four a page: the ninth is a page of its own.
        assertEquals(3, pageStarts(List(9) { 100 }, 400).size)
        // Exactly eight fill two pages and no more.
        assertEquals(2, pageStarts(List(8) { 100 }, 400).size)
    }

    @Test
    fun unevenRowsEndAPageWhereTheNextDoesNotFit() {
        val starts = pageStarts(listOf(150, 150, 150, 50, 50), 400)
        assertEquals(listOf(0, 2), starts.map { it.row })
    }

    @Test
    fun aRowTallerThanThePageTakesSeveral() {
        val starts = pageStarts(listOf(100, 900, 100), 400)
        // Page 1: row 0; then row 1 in three goes (0, 400, 800 of 900), then row 2 follows the rest.
        assertEquals(listOf(0 to 0, 1 to 0, 1 to 400, 1 to 800), starts.map { it.row to it.into })
    }

    @Test
    fun currentPageFromTheTopRow() {
        val starts = pageStarts(List(10) { 90 }, 400)
        assertEquals(1, pageOf(starts, 0, 0))
        assertEquals(1, pageOf(starts, 3, 0))
        assertEquals(2, pageOf(starts, 4, 0))
        assertEquals(3, pageOf(starts, 9, 0))
        val tall = pageStarts(listOf(900), 400)
        assertEquals(2, pageOf(tall, 0, 450))
    }

    @Test
    fun goToPageClamps() {
        assertEquals(1, clampPage(0, 6))
        assertEquals(4, clampPage(4, 6))
        assertEquals(6, clampPage(99, 6))
        assertEquals(1, clampPage(5, 0))
    }

    @Test
    fun unseenRowsCountAsTheMedian() {
        val h = RowHeights()
        h.put(0, 100)
        h.put(1, 200)
        h.put(2, 900)
        // One tall row does not drag the guess up the way an average would.
        assertEquals(listOf(100, 200, 900, 200, 200), h.list(5))
        assertEquals(listOf(0, 0), RowHeights().list(2))
    }

    @Test
    fun onlyShortListsAreMeasuredWhole() {
        assertEquals(false, shouldProbe(0))
        assertEquals(false, shouldProbe(1))
        assertEquals(true, shouldProbe(2))
        assertEquals(true, shouldProbe(PROBE_MAX_ROWS))
        assertEquals(false, shouldProbe(PROBE_MAX_ROWS + 1))
    }

    @Test
    fun theMeasuringLayoutIsSeveralScreensTall() {
        assertEquals(0, probeHeight(0))
        assertEquals(PROBE_PX, probeHeight(800))
        assertEquals(PROBE_PX * 2, probeHeight(PROBE_PX * 2))
        // Never overflows an Int, however tall the screen it is handed.
        assertEquals(true, probeHeight(Int.MAX_VALUE) > 0)
        assertEquals(true, shouldProbe(PROBE_MAX_ROWS) && !shouldProbe(PROBE_MAX_ROWS + 1) && shouldProbeLater(PROBE_MAX_ROWS + 1))
    }

    @Test
    fun theTotalDoesNotMoveOnceEveryRowIsKnown() {
        // Six paragraphs; page 1 shows only the first two, so the rest are guessed at their median.
        val real = listOf(300, 150, 500, 420, 90, 380)
        val seenOnPageOne = RowHeights().also { it.put(0, 300); it.put(1, 150) }
        // Guessing from what page one shows, the total is a page out.
        val guessed = pageStarts(seenOnPageOne.list(real.size), 800).size
        val measured = RowHeights().also { h -> real.forEachIndexed { i, v -> h.put(i, v) } }
        val total = pageStarts(measured.list(real.size), 800).size
        assertEquals(4, total)
        assertEquals(true, guessed != total)
        // Measured whole, the total is the same from page one to the last.
        assertEquals(total, pageStarts(measured.list(real.size), 800).size)
        assertEquals(real.size, measured.known(real.size))
        assertEquals(2, seenOnPageOne.known(real.size))
    }

    @Test
    fun aGapIsNotStretchedPastItsCap() {
        // Two rows on a page: one gap, 300 px of room, 48 px at most.
        assertEquals(48, spreadShare(300, 1, 48))
        // Six rows share 100 px of room: five gaps of 20, under the cap, all of it.
        assertEquals(100, spreadShare(100, 5, 48))
        assertEquals(0, spreadShare(100, 0, 48))
        assertEquals(0, spreadShare(-5, 3, 48))
    }
}
