package org.osmwatch.changesetwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.Geometry

/** [area] null = nuova area. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaEditorScreen(area: WatchArea?, onSave: (WatchArea) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var name by rememberSaveable { mutableStateOf(area?.name ?: "") }
    var bbox by remember { mutableStateOf(area?.bbox) }
    val map = remember { MapView(context) }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (area == null) R.string.new_area else R.string.edit_area)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(
                        enabled = name.isNotBlank() && bbox != null,
                        onClick = {
                            val b = bbox!!
                            val moved = area != null && area.bbox != b
                            onSave(
                                (area ?: WatchArea(name = "", minLat = 0.0, minLon = 0.0, maxLat = 0.0, maxLon = 0.0)).copy(
                                    name = name.trim(),
                                    minLat = b.minLat, minLon = b.minLon, maxLat = b.maxLat, maxLon = b.maxLon,
                                    lastCheckedAt = if (moved) null else area?.lastCheckedAt, // area cambiata: ricontrolla da capo
                                ),
                            )
                        },
                    ) { Icon(Icons.Default.Check, stringResource(R.string.save)) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.area_name)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            bbox?.let {
                val km2 = Geometry.areaKm2(it)
                val big = km2 > Geometry.WARN_AREA_KM2
                Text(
                    stringResource(R.string.area_km2, km2) + if (big) " — " + stringResource(R.string.area_too_big) else "",
                    color = if (big) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Text(stringResource(R.string.map_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = {
                    map.apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(11.0)
                        controller.setCenter(GeoPoint(area?.let { (it.minLat + it.maxLat) / 2 } ?: 41.9, area?.let { (it.minLon + it.maxLon) / 2 } ?: 12.5))
                        val dens = resources.displayMetrics.density
                        post {
                            if (overlays.isEmpty()) {
                                val start = bbox ?: boundingBox.let { // nuova area: 40% centrale della vista
                                    val dLat = (it.latNorth - it.latSouth) * 0.3
                                    val dLon = (it.lonEast - it.lonWest) * 0.3
                                    BBox(it.latSouth + dLat, it.lonWest + dLon, it.latNorth - dLat, it.lonEast - dLon)
                                        .also { b -> bbox = b }
                                }
                                if (area != null) {
                                    zoomToBoundingBox(BoundingBox(start.maxLat, start.maxLon, start.minLat, start.minLon), false, (48 * dens).toInt())
                                }
                                overlays.add(BBoxOverlay(start, dens) { bbox = it })
                                invalidate()
                            }
                        }
                    }
                },
            )
        }
    }
}
