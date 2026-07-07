package `in`.artistant.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the keyset/offset/end cursor transitions (iOS `SearchRepository.nextCursor`). */
class SearchCursorTest {

    private val limit = 20

    @Test
    fun `a short page bottoms out to End`() {
        val next = nextSearchCursor(
            rowCount = 5, limit = limit, lastScore = 80, lastId = "a",
            hasQuery = false, sort = SearchSort.Bookability, offset = 0,
        )
        assertEquals(SearchCursor.End, next)
    }

    @Test
    fun `a null last id bottoms out to End even on a nominally full page`() {
        val next = nextSearchCursor(
            rowCount = 20, limit = limit, lastScore = null, lastId = null,
            hasQuery = false, sort = SearchSort.Bookability, offset = 0,
        )
        assertEquals(SearchCursor.End, next)
    }

    @Test
    fun `full browse page on bookability advances the keyset`() {
        val next = nextSearchCursor(
            rowCount = 20, limit = limit, lastScore = 72, lastId = "z",
            hasQuery = false, sort = SearchSort.Bookability, offset = 0,
        )
        assertEquals(SearchCursor.Keyset(afterScore = 72, afterId = "z"), next)
    }

    @Test
    fun `full page on price or new advances the offset`() {
        assertEquals(
            SearchCursor.Offset(20),
            nextSearchCursor(20, limit, 10, "z", hasQuery = false, sort = SearchSort.Price, offset = 0),
        )
        assertEquals(
            SearchCursor.Offset(40),
            nextSearchCursor(20, limit, 10, "z", hasQuery = false, sort = SearchSort.New, offset = 20),
        )
    }

    @Test
    fun `a text query forces offset paging regardless of sort`() {
        val next = nextSearchCursor(
            rowCount = 20, limit = limit, lastScore = 99, lastId = "z",
            hasQuery = true, sort = SearchSort.Bookability, offset = 20,
        )
        assertEquals(SearchCursor.Offset(40), next)
    }
}
