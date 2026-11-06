package org.osmwatch.changesetwatch.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserParserTest {
    @Test fun parsesProfile() {
        val u = UserParser.parse("""<osm><user id="1" display_name="Anna" account_created="2020-01-02T03:04:05Z">
            <description>x</description><roles><moderator/></roles><changesets count="42"/><traces count="3"/>
            <blocks><received count="2" active="0"/><issued count="0" active="0"/></blocks></user></osm>""".byteInputStream())!!
        assertEquals("Anna", u.name)
        assertEquals("2020-01-02T03:04:05Z", u.createdAt.toString())
        assertEquals(42, u.changesets)
        assertEquals(2, u.blocksReceived)
        assertEquals(listOf("moderator"), u.roles)
        assertEquals(3, u.traces)
    }

    @Test fun noUserElementIsNull() {
        assertNull(UserParser.parse("<osm/>".byteInputStream()))
    }
}
