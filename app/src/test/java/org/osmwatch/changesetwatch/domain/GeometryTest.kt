package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    @Test fun area_oneDegreeAtEquator() {
        assertEquals(12393.0, Geometry.areaKm2(BBox(-0.5, 0.0, 0.5, 1.0)), 5.0)
    }

    @Test fun area_shrinksWithLatitude() {
        val eq = Geometry.areaKm2(BBox(0.0, 0.0, 1.0, 1.0))
        val at60 = Geometry.areaKm2(BBox(59.5, 0.0, 60.5, 1.0))
        assertEquals(0.5, at60 / eq, 0.01)
    }

    @Test fun apiParam_isWSEN() {
        assertEquals("12.0,41.0,13.0,42.0", BBox(41.0, 12.0, 42.0, 13.0).toApiParam())
    }
}
