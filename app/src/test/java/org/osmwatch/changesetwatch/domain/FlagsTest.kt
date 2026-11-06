package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FlagsTest {
    @Test fun headerFlags() {
        assertEquals(emptyList<Flag>(), Flags.header(10, "fix", "a", emptySet()))
        assertEquals(listOf(Flag.MANY_CHANGES, Flag.NO_COMMENT, Flag.SUSPECT_USER), Flags.header(500, " ", "bad", setOf("bad")))
    }

    @Test fun userFlags() {
        assertEquals(emptyList<Flag>(), Flags.fromUser(null))
        assertEquals(emptyList<Flag>(), Flags.fromUser(UserInfo("a", null, 10, 0)))
        assertEquals(listOf(Flag.NEW_MAPPER, Flag.PREVIOUSLY_BLOCKED), Flags.fromUser(UserInfo("a", null, 9, 1)))
    }

    private fun nodes(del: Int, other: Int) = OsmChangeSummary(
        List(del) { ChangedNode(it.toLong(), ChangeAction.DELETE, null, null) } +
            List(other) { ChangedNode(1000L + it, ChangeAction.MODIFY, 0.0, 0.0) },
        emptyList(), 0,
    )

    @Test fun massDelete() {
        assertEquals(listOf(Flag.MASS_DELETE), Flags.fromChange(nodes(20, 20)))
        assertEquals(emptyList<Flag>(), Flags.fromChange(nodes(20, 21))) // meno della metà
        assertEquals(emptyList<Flag>(), Flags.fromChange(nodes(19, 0)))  // sotto il minimo
    }
}
