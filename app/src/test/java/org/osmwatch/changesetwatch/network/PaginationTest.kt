package org.osmwatch.changesetwatch.network

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.osmwatch.changesetwatch.domain.Changeset

class PaginationTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private fun cs(id: Long, sec: Long) =
        Changeset(id, "u", 1, t0.plusSeconds(sec), null, false, null, 1, 0, emptyMap())

    @Test fun singlePage_noSecondCall() {
        var calls = 0
        val r = fetchAllPages { calls++; listOf(cs(1, 10), cs(2, 5)) }
        assertEquals(2, r.size); assertEquals(1, calls)
    }

    @Test fun fullPage_requestsNextWithOldestPlusOneSecond_andDedups() {
        val seen = mutableListOf<Instant?>()
        val r = fetchAllPages { before ->
            seen += before
            if (before == null) (0 until 100).map { cs(1000L - it, 1000L - it) } // il più vecchio: id 901, t=901
            else listOf(cs(901, 901), cs(5, 5)) // 901 ripetuto (stesso secondo)
        }
        assertEquals(listOf(null, t0.plusSeconds(902)), seen)
        assertEquals(101, r.size)
    }

    @Test fun stopsWhenNoNewIds_andAtMaxPages() {
        var calls = 0
        fetchAllPages { calls++; (0 until 100).map { cs(it.toLong(), 7) } }
        assertEquals(2, calls) // seconda pagina senza id nuovi
        calls = 0
        fetchAllPages(maxPages = 3) { b -> calls++; (0 until 100).map { cs(calls * 1000L + it, 9) } }
        assertEquals(3, calls)
    }
}
