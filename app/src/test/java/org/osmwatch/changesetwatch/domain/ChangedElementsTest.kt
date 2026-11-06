package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangedElementsTest {
    @Test fun labelUsesNameAndFirstOtherTag() {
        assertEquals("Via Roma \u00b7 highway=residential", ChangedElements.label(mapOf("name" to "Via Roma", "highway" to "residential")))
        assertEquals("shop=bakery", ChangedElements.label(mapOf("shop" to "bakery")))
        assertEquals("", ChangedElements.label(emptyMap()))
    }

    @Test fun ordersRelationsWaysThenNodesTaggedFirst() {
        val c = OsmChangeSummary(
            listOf(ChangedNode(1, ChangeAction.MODIFY, 0.0, 0.0), ChangedNode(2, ChangeAction.CREATE, 0.0, 0.0, tags = mapOf("shop" to "x"))),
            listOf(ChangedWay(10, ChangeAction.MODIFY, 1, emptyList()), ChangedWay(11, ChangeAction.MODIFY, 1, emptyList(), mapOf("highway" to "path"))),
            1, listOf(ChangedRelation(20, ChangeAction.DELETE, 2, emptyList())),
        )
        assertEquals(
            listOf(ElementType.RELATION to 20L, ElementType.WAY to 11L, ElementType.WAY to 10L, ElementType.NODE to 2L, ElementType.NODE to 1L),
            ChangedElements.list(c).map { it.ref.type to it.ref.id },
        )
    }

    @Test fun elementLinks() {
        assertEquals("https://www.openstreetmap.org/way/5/history", Links.elementHistory(ElementType.WAY, 5))
        assertEquals("https://www.openstreetmap.org/edit?editor=id&node=7", Links.editInId(ElementType.NODE, 7))
    }
}
