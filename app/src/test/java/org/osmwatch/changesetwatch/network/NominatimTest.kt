package org.osmwatch.changesetwatch.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NominatimTest {
    // risposta reale ridotta: confine comunale + punto del centro abitato con lo stesso nome, poi una provincia
    private val xml = """<?xml version="1.0" encoding="UTF-8" ?><searchresults>
        <place osm_type="node" osm_id="64778671" boundingbox="45.6312889,45.7112889,11.8861510,11.9661510" place_rank="18"
          display_name="Castelfranco Veneto, Fonte, Treviso, Veneto, 31033, Italia" class="place" type="town" geotext="POINT(11.92 45.67)"/>
        <place osm_type="relation" osm_id="45549" boundingbox="45.6130550,45.7078612,11.8677727,11.9970766" place_rank="16"
          display_name="Castelfranco Veneto, Fonte, Treviso, Veneto, 31033, Italia" class="boundary" type="administrative"
          geotext="POLYGON((11.86 45.62,11.99 45.62,11.99 45.70,11.86 45.62))"/>
        <place osm_type="relation" osm_id="41313" boundingbox="41.40,42.29,11.73,13.29" place_rank="12"
          display_name="Roma, Lazio, Italia" class="boundary" type="administrative"
          geotext="MULTIPOLYGON(((11.7 42.1,11.8 42.1,11.8 42.2,11.7 42.1)),((12.0 41.5,12.1 41.5,12.1 41.6,12.0 41.5)))"/>
        </searchresults>"""

    @Test fun boundaryWinsAndBboxOrderIsConverted() {
        val r = Nominatim.parse(xml.byteInputStream())
        assertEquals(2, r.size)
        val c = r[0]
        assertEquals("Castelfranco Veneto", c.name)
        assertEquals("Fonte, Treviso, Veneto, 31033, Italia", c.detail)
        assertEquals(PlaceKind.MUNICIPALITY, c.kind)
        assertEquals(45.6130550, c.bbox.minLat, 1e-9)
        assertEquals(11.8677727, c.bbox.minLon, 1e-9)
        assertEquals(45.7078612, c.bbox.maxLat, 1e-9)
        assertEquals(11.9970766, c.bbox.maxLon, 1e-9)
        assertEquals(1, c.outline.size)
        assertEquals(45.62, c.outline[0][0].lat, 1e-9) // WKT è "lon lat"
        assertEquals(PlaceKind.PROVINCE, r[1].kind)
        assertEquals(2, r[1].outline.size)
    }

    @Test fun pointHasNoOutline() {
        assertTrue(Nominatim.rings("POINT(11.92 45.67)").isEmpty())
    }
}
