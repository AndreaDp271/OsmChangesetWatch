package org.osmwatch.changesetwatch.ui

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlin.math.max

/** Colori e tratti per azione: nuovo = verde pieno, modificato = arancione, eliminato = rosso tratteggiato. */
object ChangeStyle {
    val NEW = 0xFF2E7D32.toInt()
    val MODIFIED = 0xFFEF6C00.toInt()
    val DELETED = 0xFFC62828.toInt()
    fun color(a: ChangeAction) = when (a) { ChangeAction.CREATE -> NEW; ChangeAction.MODIFY -> MODIFIED; ChangeAction.DELETE -> DELETED }
}

private class PointsOverlay(private val points: List<DrawPoint>, private val density: Float) : Overlay() {
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val ring = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * density; isAntiAlias = true }
    private val tmp = Point()
    override fun draw(c: Canvas, pj: Projection) {
        for (p in points) {
            pj.toPixels(GeoPoint(p.at.lat, p.at.lon), tmp)
            if (p.action == ChangeAction.DELETE) { // cerchio vuoto: elemento che non c'è più
                ring.color = ChangeStyle.DELETED
                c.drawCircle(tmp.x.toFloat(), tmp.y.toFloat(), 6f * density, ring)
            } else {
                fill.color = ChangeStyle.color(p.action)
                c.drawCircle(tmp.x.toFloat(), tmp.y.toFloat(), 5f * density, fill)
            }
        }
    }
}

/** Minimappa non invasiva: mostra i cambiamenti (o, se non ricostruibili, la bbox del changeset) e si adatta all'area. */
@Composable
fun MiniMap(geometry: ChangeGeometry?, fallback: BBox?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // dentro una colonna scrollabile: il gesto sulla mappa non deve scorrere la pagina
            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }
    AndroidView(modifier = modifier.clipToBounds(), factory = { map }, update = { m ->
        if (m.tag === geometry && m.tag != null) return@AndroidView
        m.tag = geometry
        m.overlays.clear()
        val d = m.resources.displayMetrics.density
        geometry?.lines?.forEach { l ->
            m.overlays.add(Polyline().apply {
                setPoints(l.points.map { GeoPoint(it.lat, it.lon) })
                outlinePaint.color = ChangeStyle.color(l.action)
                outlinePaint.strokeWidth = (if (l.action == ChangeAction.CREATE) 5f else 4f) * d
                if (l.action == ChangeAction.DELETE) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(10f * d, 7f * d), 0f)
            })
        }
        geometry?.points?.takeIf { it.isNotEmpty() }?.let { m.overlays.add(PointsOverlay(it, d)) }
        val pts = geometry?.allPoints().orEmpty().map { GeoPoint(it.lat, it.lon) }
        m.post {
            val box = when {
                pts.isNotEmpty() -> BoundingBox.fromGeoPoints(pts)
                fallback != null -> BoundingBox(fallback.maxLat, fallback.maxLon, fallback.minLat, fallback.minLon)
                else -> null
            }
            if (box == null) return@post
            if (max(box.latitudeSpan, box.longitudeSpan) < 1e-4) { m.controller.setZoom(18.0); m.controller.setCenter(box.centerWithDateLine) }
            else m.zoomToBoundingBox(box, false, (40 * d).toInt())
            m.invalidate()
        }
    })
}
