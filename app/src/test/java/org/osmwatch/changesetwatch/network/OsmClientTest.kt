package org.osmwatch.changesetwatch.network

import java.time.Instant
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.osmwatch.changesetwatch.domain.BBox

class OsmClientTest {
    private val server = MockWebServer()
    private val sleeps = mutableListOf<Long>()
    private val empty = """<osm version="0.6"></osm>"""

    private fun client(): OsmClient {
        server.start()
        return OsmClient(
            baseUrl = server.url("/api/0.6").toString().trimEnd('/'),
            sleep = { sleeps += it },
        )
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun request_hasUserAgentAndParams() {
        server.enqueue(MockResponse().setBody(empty))
        client()
            .fetchChangesets(
                BBox(41.0, 12.0, 42.0, 13.0),
                closedAfter = Instant.parse("2026-01-01T00:00:00Z"),
                createdBefore = Instant.parse("2026-02-01T00:00:00Z"),
            )
        val r = server.takeRequest()
        assertEquals(OsmConfig.USER_AGENT, r.getHeader("User-Agent"))
        val u = r.requestUrl!!
        assertEquals("12.0,41.0,13.0,42.0", u.queryParameter("bbox")) // W,S,E,N
        assertEquals("2026-01-01T00:00:00Z,2026-02-01T00:00:00Z", u.queryParameter("time"))
        assertEquals("100", u.queryParameter("limit"))
    }

    @Test
    fun blocks_idsFromWebPage_detailsFromApi() {
        server.start()
        val c =
            OsmClient(
                baseUrl = server.url("/api/0.6").toString().trimEnd('/'),
                webUrl = server.url("/").toString().trimEnd('/'),
                sleep = { sleeps += it },
            )
        server.enqueue(
            MockResponse()
                .setBody(
                    """<table><tr><td><a href="/user_blocks/1001">Show</a></td></tr>
                       <tr><td><a href="/user_blocks/1002">Show</a></td></tr></table>"""
                )
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """<osm><user_block id="1001" created_at="2026-10-20T07:53:39Z"
                        ends_at="2026-10-22T07:53:39Z"><user uid="1" user="Example Mapper"/>
                        <creator uid="2" user="moderator"/><reason>Dear user,
                        please stop.</reason></user_block></osm>"""
                )
        )
        server.enqueue(
            MockResponse()
                .setBody("""<osm><user_block id="1002"><reason>x</reason></user_block></osm>""")
        )
        val blocks = c.fetchBlocks("Example Mapper")
        assertEquals("/user/Example%20Mapper/blocks", server.takeRequest().path)
        assertEquals("/api/0.6/user_blocks/1001", server.takeRequest().path)
        assertEquals("/api/0.6/user_blocks/1002", server.takeRequest().path)
        assertEquals(listOf(1001L, 1002L), blocks.map { it.id })
        val b = blocks[0]
        assertEquals("moderator", b.creator)
        assertEquals(Instant.parse("2026-10-20T07:53:39Z"), b.createdAt)
        assertEquals(Instant.parse("2026-10-22T07:53:39Z"), b.endsAt)
        assertTrue(b.reason.startsWith("Dear user,") && b.reason.endsWith("please stop."))
        assertEquals(listOf(500L), sleeps) // pausa tra le due richieste all'API
    }

    @Test
    fun userRequest_hasNameAndTime() {
        server.enqueue(MockResponse().setBody(empty))
        client().fetchChangesetsByUser("Anna B", 100, Instant.parse("2026-01-01T00:00:00Z"))
        val u = server.takeRequest().requestUrl!!
        assertEquals("Anna B", u.queryParameter("display_name"))
        assertEquals("2026-01-01T00:00:00Z", u.queryParameter("time"))
        assertEquals("100", u.queryParameter("limit"))
    }

    @Test
    fun userRequest_olderPageUsesTimeRange() {
        server.enqueue(MockResponse().setBody(empty))
        client()
            .fetchChangesetsByUser(
                "Anna",
                100,
                Instant.EPOCH,
                Instant.parse("2026-03-01T00:00:00Z"),
            )
        assertEquals(
            "1970-01-01T00:00:00Z,2026-03-01T00:00:00Z",
            server.takeRequest().requestUrl!!.queryParameter("time"),
        )
    }

    @Test
    fun retries429HonoringRetryAfter_thenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7"))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(empty))
        client().fetchChangesets(BBox(0.0, 0.0, 1.0, 1.0))
        assertEquals(listOf(7000L, 2000L), sleeps) // Retry-After, poi esponenziale (attempt 1)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun givesUpAfterMaxRetries() {
        repeat(OsmConfig.MAX_RETRIES + 1) { server.enqueue(MockResponse().setResponseCode(500)) }
        try {
            client().fetchChangesets(BBox(0.0, 0.0, 1.0, 1.0))
            fail()
        } catch (e: OsmHttpException) {
            assertEquals(500, e.code)
        }
        assertEquals(OsmConfig.MAX_RETRIES + 1, server.requestCount)
    }

    @Test
    fun clientError_noRetry() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad bbox"))
        try {
            client().fetchChangesets(BBox(0.0, 0.0, 1.0, 1.0))
            fail()
        } catch (e: OsmHttpException) {
            assertEquals(400, e.code)
        }
        assertTrue(sleeps.isEmpty())
    }

    /**
     * Chiamata reale: `OSM_LIVE=1 gradlew testDebugUnitTest --tests "*liveCall*"`. Saltato di
     * default.
     */
    @Test
    fun liveCall() {
        assumeTrue(System.getenv("OSM_LIVE") == "1")
        val c = OsmClient()
        val list = c.fetchChangesets(BBox(41.88, 12.45, 41.92, 12.52), limit = 5)
        println("LIVE: ${list.size} changesets, primo=${list.firstOrNull()?.id}")
        assertTrue(list.isNotEmpty())
        val s = c.fetchOsmChange(list.first().id)
        println("LIVE: osmChange nodi=${s.nodes.size} way=${s.wayCount} rel=${s.relationCount}")
    }
}

class LiveGeometryTest {
    /**
     * `OSM_LIVE=1 OSM_CS=191555544 gradlew testDebugUnitTest --tests "*LiveGeometryTest*" -i`.
     * Saltato di default.
     */
    @Test
    fun liveGeometry() {
        assumeTrue(System.getenv("OSM_LIVE") == "1")
        val id = (System.getenv("OSM_CS") ?: "191555544").toLong()
        val c = OsmClient()
        val s = c.fetchOsmChange(id)
        val g =
            org.osmwatch.changesetwatch.domain.GeometryResolver.resolve(
                s,
                OsmElementSource(c),
                includeBefore = true,
            )
        println(
            "LIVE: nodi=${s.nodes.size} way=${s.ways.size} rel=${s.relationCount} -> punti=${g.points.size} linee=${g.lines.size} parziale=${g.partial}"
        )
        println("LIVE: before linee=${g.beforeLines.size} punti=${g.beforePoints.size}")
        println(
            "LIVE: azioni punti=${g.points.groupBy { it.action }.mapValues { it.value.size }} linee=${g.lines.groupBy { it.action }.mapValues { it.value.size }}"
        )
    }
}

class LiveVerifyTest {
    /**
     * `OSM_LIVE=1 gradlew testDebugUnitTest --tests "*LiveVerifyTest*" -i`: verdetti reali su 30
     * changeset di Roma.
     */
    @Test
    fun liveVerify() {
        assumeTrue(System.getenv("OSM_LIVE") == "1")
        val area = BBox(41.8512, 12.4705, 41.9415, 12.5447)
        val c = OsmClient()
        val list = c.fetchChangesets(area).take(30)
        val v =
            org.osmwatch.changesetwatch.domain.ChangesetVerifier(
                2000,
                {
                    Thread.sleep(700)
                    c.fetchOsmChange(it)
                },
                OsmElementSource(c, 300),
            )
        val res = list.map { cs -> cs to v.verify(cs.id, cs.bbox, cs.changesCount, area) }
        println("LIVE: " + res.groupingBy { it.second }.eachCount())
        res.filter { it.second != org.osmwatch.changesetwatch.domain.Verdict.CONFIRMED }
            .forEach {
                println(
                    "LIVE: ${it.second} #${it.first.id} changes=${it.first.changesCount} bbox=${it.first.bbox}"
                )
            }
    }
}
