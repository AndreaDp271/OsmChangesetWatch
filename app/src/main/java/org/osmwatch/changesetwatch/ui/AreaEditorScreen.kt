package org.osmwatch.changesetwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.domain.AreaShape
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.Geometry
import org.osmwatch.changesetwatch.network.Nominatim
import org.osmwatch.changesetwatch.network.OsmClient
import org.osmwatch.changesetwatch.network.Place
import org.osmwatch.changesetwatch.network.PlaceKind

/**
 * [area] null = nuova area. Mappa a tutto schermo; in alto la ricerca di un luogo (comune, provincia...), in basso nome e salvataggio.
 * Scegliere un comune con confine fa dell'area il confine stesso (verifica precisa); "Usa un rettangolo" torna al rettangolo libero.
 */
@Composable
fun AreaEditorScreen(area: WatchArea?, onSave: (WatchArea) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current
    val lang = LocalConfiguration.current.locales[0].language
    val scope = rememberCoroutineScope()
    val http = remember { OsmClient.defaultHttp() }
    val primary = MaterialTheme.colorScheme.primary.toArgb()

    var name by rememberSaveable { mutableStateOf(area?.name ?: "") }
    var autoName by remember { mutableStateOf<String?>(null) } // nome messo dalla ricerca: si aggiorna se l'utente non l'ha cambiato
    var bbox by remember { mutableStateOf(area?.bbox) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var lastSearchAt by remember { mutableLongStateOf(0L) }
    // confine scelto (anelli): se c'è, l'area è il confine e il rettangolo libero sparisce
    var boundary by remember { mutableStateOf(AreaShape.decode(area?.polygon).takeIf { it.isNotEmpty() }) }
    var boundaryName by remember { mutableStateOf(if (area?.polygon != null) area.name else null) }
    var boundaryLoading by remember { mutableStateOf(false) }

    val map = remember { MapView(context) }
    val box = remember { arrayOfNulls<BBoxOverlay>(1) }
    val shapes = remember { mutableListOf<Overlay>() }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        focus.clearFocus()
        scope.launch {
            searching = true
            searchError = null
            try {
                val wait = 1_100 - (System.currentTimeMillis() - lastSearchAt) // policy Nominatim: max 1 richiesta al secondo
                if (wait > 0) delay(wait)
                results = withContext(Dispatchers.IO) { Nominatim.search(http, q, lang) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results = null
                searchError = e.message ?: e.javaClass.simpleName
            } finally {
                lastSearchAt = System.currentTimeMillis()
                searching = false
            }
        }
    }

    /** Disegna il confine (riempito, non modificabile) oppure il rettangolo trascinabile. */
    fun render() {
        val dens = context.resources.displayMetrics.density
        shapes.forEach { map.overlays.remove(it) }
        shapes.clear()
        val rings = boundary
        val rect = box[0]
        if (rings != null) {
            rect?.let { map.overlays.remove(it) }
            rings.forEach { ring ->
                Polygon(map).apply {
                    setPoints(ring.map { GeoPoint(it.lat, it.lon) })
                    fillPaint.color = (primary and 0x00FFFFFF) or 0x2E000000
                    outlinePaint.color = primary
                    outlinePaint.strokeWidth = 2.5f * dens
                    infoWindow = null
                    setOnClickListener { _, _, _ -> true } // solo da vedere: nessun fumetto al tocco
                }.also { map.overlays.add(0, it); shapes += it }
            }
        } else if (rect != null) {
            bbox?.let { rect.bbox = it }
            if (rect !in map.overlays) map.overlays.add(rect)
        }
        map.invalidate()
    }

    fun choose(p: Place) {
        val dens = context.resources.displayMetrics.density
        bbox = p.bbox
        boundary = p.outline.takeIf { it.isNotEmpty() }
        boundaryName = if (boundary != null) p.name else null
        render()
        map.zoomToBoundingBox(BoundingBox(p.bbox.maxLat, p.bbox.maxLon, p.bbox.minLat, p.bbox.minLon), true, (72 * dens).toInt())
        if (name.isBlank() || name == autoName) { name = p.name; autoName = p.name }
        query = p.name
        results = null
        // confine meno semplificato (~20 m): sostituisce quello della ricerca se nel frattempo non hai cambiato scelta
        val ref = p.osmRef ?: return
        if (boundary == null) return
        scope.launch {
            boundaryLoading = true
            try {
                val wait = 1_100 - (System.currentTimeMillis() - lastSearchAt)
                if (wait > 0) delay(wait)
                val detailed = withContext(Dispatchers.IO) { Nominatim.lookup(http, ref, lang) }
                if (detailed != null && detailed.outline.isNotEmpty() && boundary != null && boundaryName == p.name) {
                    boundary = detailed.outline
                    bbox = detailed.bbox
                    render()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // resta il confine della ricerca, un po' più semplificato
            } finally {
                lastSearchAt = System.currentTimeMillis()
                boundaryLoading = false
            }
        }
    }

    fun useRectangle() {
        boundary = null
        boundaryName = null
        render()
    }

    fun save() {
        val b = bbox ?: return
        val polygon = boundary?.let(AreaShape::encode)
        val moved = area != null && (area.bbox != b || area.polygon != polygon)
        onSave(
            (area ?: WatchArea(name = "", minLat = 0.0, minLon = 0.0, maxLat = 0.0, maxLon = 0.0)).copy(
                name = name.trim(),
                minLat = b.minLat, minLon = b.minLon, maxLat = b.maxLat, maxLon = b.maxLon,
                polygon = polygon,
                lastCheckedAt = if (moved) null else area?.lastCheckedAt, // area cambiata: ricontrolla da capo
            ),
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clipToBounds(), // MapView non clippa da solo
            factory = {
                map.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(13.0)
                    controller.setCenter(GeoPoint(area?.let { (it.minLat + it.maxLat) / 2 } ?: 41.9, area?.let { (it.minLon + it.maxLon) / 2 } ?: 12.5))
                    val dens = resources.displayMetrics.density
                    post {
                        if (box[0] == null) { // primo disegno: crea il rettangolo (resta nascosto se c'è un confine)
                            val start = bbox ?: boundingBox.let { // nuova area: 40% centrale della vista
                                val dLat = (it.latNorth - it.latSouth) * 0.3
                                val dLon = (it.lonEast - it.lonWest) * 0.3
                                BBox(it.latSouth + dLat, it.lonWest + dLon, it.latNorth - dLat, it.lonEast - dLon)
                                    .also { b -> bbox = b }
                            }
                            if (area != null) {
                                zoomToBoundingBox(BoundingBox(start.maxLat, start.maxLon, start.minLat, start.minLon), false, (48 * dens).toInt())
                            }
                            box[0] = BBoxOverlay(start, dens) { bbox = it }
                            render()
                        }
                    }
                }
            },
        )

        // in alto: indietro + ricerca, con i risultati sotto
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    TextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_place_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        searching -> CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.5.dp)
                        results != null || searchError != null -> IconButton(onClick = { results = null; searchError = null }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) }
                        else -> IconButton(onClick = ::search, enabled = query.isNotBlank()) { Icon(Icons.Default.Search, stringResource(R.string.search_place_hint)) }
                    }
                }
            }
            val list = results
            if (list != null || searchError != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column {
                        when {
                            searchError != null -> Text(
                                stringResource(R.string.search_place_error, searchError ?: ""), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
                            )
                            list.isNullOrEmpty() -> Text(stringResource(R.string.search_place_none, query.trim()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                            else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                                items(list) { p ->
                                    PlaceRow(p) { choose(p) }
                                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                        Muted(stringResource(R.string.search_attribution), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 2.dp))
                    }
                }
            }
        }

        // in basso: nome, superficie, salva
        Surface(
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.area_name)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                bbox?.let {
                    val km2 = boundary?.let { r -> AreaShape(it, r).areaKm2() } ?: Geometry.areaKm2(it)
                    val big = km2 > Geometry.WARN_AREA_KM2
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (big) Icons.Default.Warning else Icons.Default.SquareFoot, null, Modifier.size(20.dp),
                            tint = if (big) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.area_km2, km2), style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.SemiBold,
                            color = if (big) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (big) Text(stringResource(R.string.area_too_big), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                if (boundary != null) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationCity, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(stringResource(R.string.boundary_of, boundaryName ?: name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Muted(stringResource(if (boundaryLoading) R.string.boundary_loading else R.string.boundary_hint))
                        }
                        TextButton(onClick = ::useRectangle) { Text(stringResource(R.string.use_rectangle)) }
                    }
                } else {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.OpenWith, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Muted(stringResource(R.string.map_hint), Modifier.padding(start = 8.dp))
                    }
                }
                Button(onClick = ::save, enabled = name.isNotBlank() && bbox != null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.save_area), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(p: Place, onClick: () -> Unit) {
    val (icon, label) = when (p.kind) {
        PlaceKind.REGION -> Icons.Default.Public to R.string.kind_region
        PlaceKind.PROVINCE -> Icons.Default.Map to R.string.kind_province
        PlaceKind.MUNICIPALITY -> Icons.Default.LocationCity to R.string.kind_municipality
        PlaceKind.LOCALITY -> Icons.Default.Place to R.string.kind_locality
        PlaceKind.OTHER -> Icons.Default.Place to R.string.kind_place
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.detail.isNotEmpty()) Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Pill(stringResource(label), MaterialTheme.colorScheme.primary, Modifier.padding(start = 8.dp))
    }
}
