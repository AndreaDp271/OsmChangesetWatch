package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeKindTest {
    @Test
    fun kinds() {
        assertTrue(ChangeKind.ANY.matches(null, null, null))
        assertFalse(ChangeKind.HAS_DELETES.matches(null, null, null)) // senza conteggi: escluso
        assertTrue(ChangeKind.HAS_DELETES.matches(5, 1, 1))
        assertFalse(ChangeKind.ONLY_DELETES.matches(0, 1, 3))
        assertTrue(ChangeKind.ONLY_DELETES.matches(0, 0, 3))
        assertTrue(ChangeKind.ONLY_CREATES.matches(4, 0, 0))
        assertFalse(ChangeKind.ONLY_CREATES.matches(4, 0, 1))
    }

    @Test
    fun massDeleteFromCounts() {
        assertEquals(listOf(Flag.MASS_DELETE), Flags.fromCounts(45, 8, 53))
        assertEquals(emptyList<Flag>(), Flags.fromCounts(45, 8, 20)) // meno della metà
        assertEquals(emptyList<Flag>(), Flags.fromCounts(null, 0, 99))
    }
}
