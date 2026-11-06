package org.osmwatch.changesetwatch.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmwatch.changesetwatch.domain.BBox
import kotlin.math.hypot

/** Rettangolo editabile: trascina un angolo per ridimensionare, l'interno per spostare. Fuori: la mappa si muove. */
class BBoxOverlay(
    var bbox: BBox,
    private val density: Float,
    private val onChange: (BBox) -> Unit,
) : Overlay() {

    private val fill = Paint().apply { color = 0x332196F3; style = Paint.Style.FILL }
    private val stroke = Paint().apply { color = 0xFF1565C0.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f * density; isAntiAlias = true }
    private val handle = Paint().apply { color = 0xFF1565C0.toInt(); style = Paint.Style.FILL; isAntiAlias = true }

    private var corner = -1 // 0 NO, 1 NE, 2 SE, 3 SO; -1 nessuno
    private var moving = false
    private var last: GeoPoint? = null

    private fun corners(pj: Projection): List<Point> = listOf(
        pj.toPixels(GeoPoint(bbox.maxLat, bbox.minLon), null),
        pj.toPixels(GeoPoint(bbox.maxLat, bbox.maxLon), null),
        pj.toPixels(GeoPoint(bbox.minLat, bbox.maxLon), null),
        pj.toPixels(GeoPoint(bbox.minLat, bbox.minLon), null),
    )

    override fun draw(c: Canvas, pj: Projection) {
        val p = corners(pj)
        c.drawRect(p[0].x.toFloat(), p[0].y.toFloat(), p[2].x.toFloat(), p[2].y.toFloat(), fill)
        c.drawRect(p[0].x.toFloat(), p[0].y.toFloat(), p[2].x.toFloat(), p[2].y.toFloat(), stroke)
        p.forEach { c.drawCircle(it.x.toFloat(), it.y.toFloat(), 8f * density, handle) }
    }

    override fun onTouchEvent(e: MotionEvent, map: MapView): Boolean {
        val pj = map.projection
        val geo = pj.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = corners(pj)
                val dists = p.map { hypot((it.x - e.x).toDouble(), (it.y - e.y).toDouble()) }
                val nearest = dists.indices.minByOrNull { dists[it] }!!
                corner = if (dists[nearest] < 32 * density) nearest else -1
                moving = corner < 0 &&
                    e.x in p[0].x.toFloat()..p[2].x.toFloat() && e.y in p[0].y.toFloat()..p[2].y.toFloat()
                last = geo
                return corner >= 0 || moving
            }
            MotionEvent.ACTION_MOVE -> {
                if (corner < 0 && !moving) return false
                val b = bbox
                val next = if (moving) {
                    val dLat = geo.latitude - last!!.latitude
                    val dLon = geo.longitude - last!!.longitude
                    BBox(b.minLat + dLat, b.minLon + dLon, b.maxLat + dLat, b.maxLon + dLon)
                } else when (corner) {
                    0 -> b.copy(maxLat = geo.latitude, minLon = geo.longitude)
                    1 -> b.copy(maxLat = geo.latitude, maxLon = geo.longitude)
                    2 -> b.copy(minLat = geo.latitude, maxLon = geo.longitude)
                    else -> b.copy(minLat = geo.latitude, minLon = geo.longitude)
                }
                // ignora mosse che invertono il rettangolo o escono dai limiti Web Mercator
                if (next.minLat < next.maxLat && next.minLon < next.maxLon &&
                    next.minLat > -85.0 && next.maxLat < 85.0 && next.minLon > -180.0 && next.maxLon < 180.0
                ) {
                    bbox = next
                    onChange(next)
                    map.invalidate()
                }
                last = geo
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val active = corner >= 0 || moving
                corner = -1; moving = false
                return active
            }
        }
        return false
    }
}
