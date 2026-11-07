package org.osmwatch.changesetwatch.ui

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.hypot
import kotlin.math.max
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.ChangeAction
import org.osmwatch.changesetwatch.domain.ChangeGeometry
import org.osmwatch.changesetwatch.domain.DrawPoint
import org.osmwatch.changesetwatch.domain.ElementRef

/**
 * Colori e tratti per azione: nuovo = verde pieno, modificato = arancione, eliminato = rosso
 * tratteggiato.
 */
object ChangeStyle {
    val NEW = 0xFF2E7D32.toInt()
    val MODIFIED = 0xFFEF6C00.toInt()
    val DELETED = 0xFFC62828.toInt()
    val BEFORE = 0xFF757575.toInt()

    fun color(a: ChangeAction) =
        when (a) {
            ChangeAction.CREATE -> NEW
            ChangeAction.MODIFY -> MODIFIED
            ChangeAction.DELETE -> DELETED
        }
}

private class PointsOverlay(
    private val points: List<DrawPoint>,
    private val beforePoints: List<org.osmwatch.changesetwatch.domain.LatLon>,
    private val density: Float,
) : Overlay() {
    private val fill =
        Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    private val ring =
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            isAntiAlias = true
        }
    private val tmp = Point()

    override fun draw(c: Canvas, pj: Projection) {
        ring.color = ChangeStyle.BEFORE
        for (b in beforePoints) { // posizione precedente di un nodo spostato
            pj.toPixels(GeoPoint(b.lat, b.lon), tmp)
            c.drawCircle(tmp.x.toFloat(), tmp.y.toFloat(), 3.5f * density, ring)
        }
        for (p in points) {
            pj.toPixels(GeoPoint(p.at.lat, p.at.lon), tmp)
            if (p.action == ChangeAction.DELETE) { // cerchio vuoto: elemento che non c'è più
                ring.color = ChangeStyle.DELETED
                c.drawCircle(tmp.x.toFloat(), tmp.y.toFloat(), 4f * density, ring)
            } else {
                fill.color = ChangeStyle.color(p.action)
                c.drawCircle(tmp.x.toFloat(), tmp.y.toFloat(), 3.5f * density, fill)
            }
        }
    }
}

/**
 * I tile OSM arrivano al 19; oltre, osmdroid ingrandisce quelli del 19 (fino a 29, illeggibile).
 * Due livelli in più (tile ×4) restano leggibili e le geometrie disegnate sopra sono sempre nitide.
 */
const val MAX_ZOOM = 21.0
private val TILE_ZOOM = TileSourceFactory.MAPNIK.maximumZoomLevel.toDouble()

/**
 * Minimappa non invasiva: mostra i cambiamenti (o, se non ricostruibili, la bbox del changeset) e
 * si adatta all'area.
 */
@Composable
fun MiniMap(
    geometry: ChangeGeometry?,
    fallback: BBox?,
    onElementTap: (ElementRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapState = rememberUpdatedState(onElementTap)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            maxZoomLevel = MAX_ZOOM
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // dentro una colonna scrollabile: il gesto sulla mappa non deve scorrere la pagina
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            map.onDetach()
        }
    }
    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { map },
        update = { m ->
            if (m.tag === geometry && m.tag != null) return@AndroidView
            m.tag = geometry
            m.overlays.clear()
            val d = m.resources.displayMetrics.density
            geometry?.beforeLines?.forEach { l -> // prima della modifica: sotto, grigio sottile
                m.overlays.add(
                    Polyline().apply {
                        setPoints(l.points.map { GeoPoint(it.lat, it.lon) })
                        outlinePaint.color = ChangeStyle.BEFORE
                        outlinePaint.strokeWidth = 1.5f * d
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(3f * d, 4f * d), 0f)
                    }
                )
            }
            geometry?.lines?.forEach { l ->
                m.overlays.add(
                    Polyline().apply {
                        setPoints(l.points.map { GeoPoint(it.lat, it.lon) })
                        outlinePaint.color = ChangeStyle.color(l.action)
                        outlinePaint.strokeWidth =
                            (if (l.action == ChangeAction.CREATE) 2.5f else 2f) * d
                        if (l.action == ChangeAction.DELETE)
                            outlinePaint.pathEffect =
                                DashPathEffect(floatArrayOf(6f * d, 4f * d), 0f)
                    }
                )
            }
            geometry
                ?.takeIf { it.points.isNotEmpty() || it.beforePoints.isNotEmpty() }
                ?.let { m.overlays.add(PointsOverlay(it.points, it.beforePoints, d)) }
            if (geometry != null && !geometry.isEmpty)
                m.overlays.add(TapOverlay(geometry, d) { tapState.value(it) })
            val pts = geometry?.allPoints().orEmpty().map { GeoPoint(it.lat, it.lon) }
            m.post {
                val box =
                    when {
                        pts.isNotEmpty() -> BoundingBox.fromGeoPoints(pts)
                        fallback != null ->
                            BoundingBox(
                                fallback.maxLat,
                                fallback.maxLon,
                                fallback.minLat,
                                fallback.minLon,
                            )
                        else -> null
                    }
                if (box == null) return@post
                if (max(box.latitudeSpan, box.longitudeSpan) < 1e-4) {
                    m.controller.setZoom(18.0)
                    m.controller.setCenter(box.centerWithDateLine)
                } else {
                    m.zoomToBoundingBox(box, false, (40 * d).toInt())
                    // oltre il 19 i tile non si scaricano: si apre al 19 (così finiscono in cache)
                    // e lo zoom in più lo fa l'utente
                    if (m.zoomLevelDouble > TILE_ZOOM) m.controller.setZoom(TILE_ZOOM)
                }
                m.invalidate()
            }
        },
    )
}

/**
 * Tocco sulla mappa: l'elemento più vicino entro ~28 dp (punto, o tratto di una way) viene
 * segnalato.
 */
private class TapOverlay(
    private val geometry: ChangeGeometry,
    private val density: Float,
    private val onTap: (ElementRef) -> Unit,
) : Overlay() {
    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val pj = map.projection
        val maxD = 28f * density
        var best: ElementRef? = null
        var bestD = Float.MAX_VALUE
        val a = Point()
        val b = Point()
        for (p in geometry.points) {
            pj.toPixels(GeoPoint(p.at.lat, p.at.lon), a)
            val dd = hypot(a.x - e.x, a.y - e.y)
            if (dd < bestD) {
                bestD = dd
                best = p.ref
            }
        }
        for (l in geometry.lines) {
            var prev: Point? = null
            for (pt in l.points) {
                val cur = Point()
                pj.toPixels(GeoPoint(pt.lat, pt.lon), cur)
                prev?.let { q ->
                    val dd =
                        distToSegment(
                            e.x,
                            e.y,
                            q.x.toFloat(),
                            q.y.toFloat(),
                            cur.x.toFloat(),
                            cur.y.toFloat(),
                        )
                    if (dd < bestD) {
                        bestD = dd
                        best = l.ref
                    }
                }
                prev = cur
            }
        }
        val hit = best
        if (hit != null && bestD <= maxD) {
            onTap(hit)
            return true
        }
        return false
    }

    private fun distToSegment(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
