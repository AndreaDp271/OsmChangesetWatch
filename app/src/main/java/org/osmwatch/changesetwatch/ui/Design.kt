package org.osmwatch.changesetwatch.ui

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.max
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.LatLon
import org.osmwatch.changesetwatch.domain.Verdict

/** Cifre a larghezza fissa per id e conteggi (colonne allineate da riga a riga). */
fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

val ChangesetRow.bbox: BBox?
    get() =
        if (bboxMinLat != null) BBox(bboxMinLat, bboxMinLon!!, bboxMaxLat!!, bboxMaxLon!!) else null

fun verdictLabel(v: Verdict): Int =
    when (v) {
        Verdict.PENDING -> R.string.v_pending
        Verdict.CONFIRMED -> R.string.v_confirmed
        Verdict.LIKELY -> R.string.v_likely
        Verdict.UNCERTAIN -> R.string.v_uncertain
        Verdict.OUTSIDE -> R.string.v_outside
        Verdict.SKIPPED_TOO_LARGE -> R.string.v_too_large
    }

/** Rettangolo nel sistema "piatto" del glifo: x = lon·cos(lat medio), y = −lat (nord in alto). */
private data class Flat(val l: Double, val t: Double, val r: Double, val b: Double)

private fun BBox.flat(k: Double) = Flat(minLon * k, -maxLat, maxLon * k, -minLat)

/**
 * Impronta: l'area osservata (contorno tratteggiato) e la bbox del changeset (riempita) nella
 * stessa scala. Si vede subito dove cade la modifica e se sborda dall'area. Senza area (utente
 * seguito, ricerca globale) mostra [fallbackIcon]. La vista è l'area allargata del 60% per lato:
 * una bbox enorme esce dai bordi invece di ridurre l'area a un punto.
 */
@Composable
fun Footprint(
    area: BBox?,
    change: BBox?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    unseen: Boolean = false,
    fallbackIcon: ImageVector = Icons.Filled.Public,
    areaRings: List<List<LatLon>> = emptyList(),
    fillArea: Boolean = false,
) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val dot = MaterialTheme.colorScheme.primary
    Box(modifier.size(size)) {
        Box(
            Modifier.size(size)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (area == null) {
                Icon(fallbackIcon, null, tint = color, modifier = Modifier.size(size * 0.5f))
            } else {
                Canvas(Modifier.size(size)) {
                    val k = cos(Math.toRadians((area.minLat + area.maxLat) / 2))
                    val a = area.flat(k)
                    val padX = max(a.r - a.l, 1e-6) * 0.6
                    val padY = max(a.b - a.t, 1e-6) * 0.6
                    val view = Flat(a.l - padX, a.t - padY, a.r + padX, a.b + padY)
                    val scale =
                        minOf(
                            this.size.width / (view.r - view.l),
                            this.size.height / (view.b - view.t),
                        )
                    val ox = (this.size.width - (view.r - view.l) * scale) / 2
                    val oy = (this.size.height - (view.b - view.t) * scale) / 2
                    fun x(v: Double) =
                        (ox + (v - view.l) * scale).toFloat().coerceIn(-2f, this.size.width + 2f)
                    fun y(v: Double) =
                        (oy + (v - view.t) * scale).toFloat().coerceIn(-2f, this.size.height + 2f)
                    val stroke = 1.5.dp.toPx()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                    if (areaRings.isEmpty()) {
                        if (fillArea)
                            drawRect(
                                color.copy(alpha = 0.28f),
                                Offset(x(a.l), y(a.t)),
                                Size(x(a.r) - x(a.l), y(a.b) - y(a.t)),
                            )
                        drawRoundRect(
                            if (fillArea) color else outline,
                            Offset(x(a.l), y(a.t)),
                            Size(x(a.r) - x(a.l), y(a.b) - y(a.t)),
                            CornerRadius(2.dp.toPx()),
                            style = Stroke(stroke, pathEffect = if (fillArea) null else dash),
                        )
                    } else {
                        // confine vero: stesso sistema di coordinate del rettangolo
                        val path =
                            Path().apply {
                                fillType = PathFillType.EvenOdd
                                areaRings.forEach { r ->
                                    r.forEachIndexed { i, pt ->
                                        val px = x(pt.lon * k)
                                        val py = y(-pt.lat)
                                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                                    }
                                    close()
                                }
                            }
                        if (fillArea) drawPath(path, color.copy(alpha = 0.28f))
                        drawPath(
                            path,
                            if (fillArea) color else outline,
                            style = Stroke(stroke, pathEffect = if (fillArea) null else dash),
                        )
                    }
                    change?.flat(k)?.let { c ->
                        val tl = Offset(x(c.l), y(c.t))
                        val sz = Size(x(c.r) - x(c.l), y(c.b) - y(c.t))
                        if (sz.width < 4.dp.toPx() && sz.height < 4.dp.toPx()) {
                            drawCircle(
                                color,
                                3.5.dp.toPx(),
                                Offset(tl.x + sz.width / 2, tl.y + sz.height / 2),
                            )
                        } else {
                            drawRect(color.copy(alpha = 0.28f), tl, sz)
                            drawRect(color, tl, sz, style = Stroke(stroke))
                        }
                    }
                }
            }
        }
        if (unseen)
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = 0.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
    }
}

/** Etichetta compatta: testo colorato su fondo dello stesso colore molto chiaro. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Numero grande con didascalia (profilo autore); con [onClick] il numero è un link. */
@Composable
fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        if (onClick == null) modifier
        else
            modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.tabular(),
            fontWeight = FontWeight.SemiBold,
            color = if (onClick == null) Color.Unspecified else MaterialTheme.colorScheme.primary,
            textDecoration = if (onClick == null) null else TextDecoration.Underline,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Icona piccola + testo, per i metadati di una riga (al posto delle stringhe unite da punti). */
@Composable
fun Meta(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Colore del segno sull'impronta: rosso se ci sono flag, blu se nell'area, grigio altrimenti. */
@Composable
fun footprintColor(verdict: Verdict, flagged: Boolean): Color =
    when {
        flagged -> MaterialTheme.colorScheme.error
        verdict == Verdict.CONFIRMED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

@Composable fun verdictText(v: Verdict) = stringResource(verdictLabel(v))

/**
 * Proporzione di creazioni, modifiche e cancellazioni (verde, arancio, rosso) con i numeri accanto.
 * Niente se il server non ha dato i conteggi (changeset salvati prima di questa versione).
 */
@Composable
fun CountsBar(created: Int?, modified: Int?, deleted: Int?, modifier: Modifier = Modifier) {
    if (created == null || modified == null || deleted == null || created + modified + deleted == 0)
        return
    val parts =
        listOf(
            created to ChangeStyle.NEW,
            modified to ChangeStyle.MODIFIED,
            deleted to ChangeStyle.DELETED,
        )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))) {
            parts.forEach { (n, c) ->
                if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(Color(c)))
            }
        }
        Text(
            "+$created  ~$modified  −$deleted",
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * "4 ore fa" nella lingua dell'app: DateUtils.getRelativeTimeSpanString segue la lingua di sistema
 * e ignora quella scelta nelle impostazioni.
 */
@Composable
fun relativeTime(ms: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val f = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
    val past = RelativeDateTimeFormatter.Direction.LAST
    val min = (System.currentTimeMillis() - ms).coerceAtLeast(0) / 60_000
    return when {
        min < 1 ->
            f.format(
                RelativeDateTimeFormatter.Direction.PLAIN,
                RelativeDateTimeFormatter.AbsoluteUnit.NOW,
            )
        min < 60 -> f.format(min.toDouble(), past, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
        min < 24 * 60 ->
            f.format((min / 60).toDouble(), past, RelativeDateTimeFormatter.RelativeUnit.HOURS)
        min < 2 * 24 * 60 -> f.format(past, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
        min < 7 * 24 * 60 ->
            f.format(
                (min / (24 * 60)).toDouble(),
                past,
                RelativeDateTimeFormatter.RelativeUnit.DAYS,
            )
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(ms))
    }
}
