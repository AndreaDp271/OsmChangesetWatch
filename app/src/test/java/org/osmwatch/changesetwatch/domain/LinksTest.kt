package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    @Test fun parsesChangesetId() {
        assertEquals(42L, Links.changesetId("https://www.openstreetmap.org/changeset/42"))
        assertEquals(42L, Links.changesetId("https://osm.org/changeset/42/"))
        assertEquals(42L, Links.changesetId("https://osmcha.org/changesets/42?x=1"))
        assertEquals(null, Links.changesetId("https://www.openstreetmap.org/node/42"))
        assertEquals(null, Links.changesetId("https://www.openstreetmap.org/changeset/abc"))
    }

    @Test fun templatesFillId() {
        assertEquals("https://www.openstreetmap.org/changeset/42", Links.url(Links.OSM, 42))
        assertEquals("https://overpass-api.de/achavi/?changeset=42", Links.url(Links.ACHAVI, 42))
        assertEquals("https://osmcha.org/changesets/42", Links.url(Links.OSMCHA, 42))
    }
}
