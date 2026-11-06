package org.osmwatch.changesetwatch.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.osmwatch.changesetwatch.domain.BBox
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.domain.Links

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesetDetailScreen(
    row: ChangesetRow?, areaName: String?, geo: GeoState, onLoadGeometry: (ChangesetRow) -> Unit, onMarkSeen: () -> Unit, onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(row?.id) { row?.let(onLoadGeometry) }
    val context = LocalContext.current
    fun open(template: String) {
        val url = Links.url(template, row!!.id)
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: ActivityNotFoundException) {}
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#${row?.id ?: ""}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { pad ->
        if (row == null) return@Scaffold
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            val fmt = { t: Long -> DateFormat.format("yyyy-MM-dd HH:mm", t).toString() }
            Text(row.comment ?: stringResource(R.string.no_comment), style = MaterialTheme.typography.titleMedium)
            Field(R.string.f_user, "${row.user ?: "?"}${row.uid?.let { " ($it)" } ?: ""}")
            Field(R.string.f_area, areaName ?: "")
            Field(R.string.f_created, fmt(row.createdAt))
            Field(R.string.f_closed, row.closedAt?.let(fmt) ?: stringResource(R.string.f_open))
            Field(R.string.f_editor, row.createdBy ?: "?")
            row.source?.let { Field(R.string.f_source, it) }
            Field(R.string.f_changes, (row.changesCount ?: 0).toString())
            if (row.bboxMinLat != null) Field(R.string.f_bbox, "%.5f,%.5f → %.5f,%.5f".format(row.bboxMinLon, row.bboxMinLat, row.bboxMaxLon, row.bboxMaxLat))
            Field(R.string.f_verdict, row.verdict.name)
            ChangesMap(row, geo)
            Button(onClick = { open(Links.OSM) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(stringResource(R.string.open_osm)) }
            OutlinedButton(onClick = { open(Links.ACHAVI) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_achavi)) }
            OutlinedButton(onClick = { open(Links.OSMCHA) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_osmcha)) }
            OutlinedButton(onClick = onMarkSeen, enabled = !row.seen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (row.seen) R.string.seen else R.string.mark_seen))
            }
        }
    }
}

@Composable
private fun Field(label: Int, value: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
        Text(value)
    }
}

@Composable
private fun ChangesMap(row: ChangesetRow, geo: GeoState) {
    val g = (geo as? GeoState.Ready)?.geometry
    val bbox = if (row.bboxMinLat != null) BBox(row.bboxMinLat, row.bboxMinLon!!, row.bboxMaxLat!!, row.bboxMaxLon!!) else null
    Text(stringResource(R.string.map_title), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    MiniMap(g, bbox, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp)))
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Legend(ChangeStyle.NEW, R.string.legend_new)
        Legend(ChangeStyle.MODIFIED, R.string.legend_modified)
        Legend(ChangeStyle.DELETED, R.string.legend_deleted)
    }
    val note = when {
        geo is GeoState.Loading -> R.string.geo_loading
        geo is GeoState.TooLarge -> R.string.geo_too_large
        geo is GeoState.Failed -> R.string.geo_failed
        g != null && g.isEmpty -> R.string.geo_empty
        g != null && g.partial -> R.string.geo_partial
        else -> null
    }
    if (note != null) Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Legend(color: Int, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(color)))
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
    }
}
