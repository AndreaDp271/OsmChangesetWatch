package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    @Test fun templatesFillId() {
        assertEquals("https://www.openstreetmap.org/changeset/42", Links.url(Links.OSM, 42))
        assertEquals("https://overpass-api.de/achavi/?changeset=42", Links.url(Links.ACHAVI, 42))
        assertEquals("https://osmcha.org/changesets/42", Links.url(Links.OSMCHA, 42))
    }
}
