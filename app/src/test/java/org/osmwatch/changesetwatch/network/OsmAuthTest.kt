package org.osmwatch.changesetwatch.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmAuthTest {
    @Test fun challengeMatchesRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OsmAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun randomTokenIsUrlSafeAndFresh() {
        val a = OsmAuth.randomToken()
        assertEquals(43, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(a, OsmAuth.randomToken())
    }

    @Test fun authorizeUrlCarriesPkceAndNoSecret() {
        val u = OsmAuth.authorizeUrl("CH", "ST")
        assertTrue(u.startsWith("https://www.openstreetmap.org/oauth2/authorize?"))
        assertTrue("code_challenge=CH" in u && "code_challenge_method=S256" in u && "state=ST" in u)
        assertTrue("scope=write_changeset_comments" in u && "client_secret" !in u)
    }
}
