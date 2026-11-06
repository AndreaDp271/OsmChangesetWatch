package org.osmwatch.changesetwatch.domain

import java.time.Instant
import java.time.ZoneOffset

/**
 * Riassunto stile HDYC calcolato in locale sugli ultimi changeset dell'utente (non sulla storia
 * completa).
 */
data class UserActivity(
    val sample: Int,
    val from: Instant,
    val to: Instant,
    val activeDays: Int,
    /** Editor e percentuale, dal più usato. */
    val editors: List<Pair<String, Int>>,
    val avgChanges: Int,
    val maxChanges: Int,
    val noCommentPct: Int,
    val hashtags: List<String>,
    /** Quanti changeset del campione toccano (bbox) almeno una delle tue aree. */
    val inAreas: Int,
)

object UserActivityCalc {
    private fun pct(part: Int, total: Int) = (part * 100 + total / 2) / total

    fun of(list: List<Changeset>, areas: List<BBox>): UserActivity? {
        if (list.isEmpty()) return null
        val n = list.size
        val changes = list.map { it.changesCount ?: 0 }
        val editors =
            list
                .groupingBy { RowTags.editor(it.createdBy) ?: "?" }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key to pct(it.value, n) }
        // hashtag dal tag "hashtags" (iD: "#a;#b") e dal commento, una volta per changeset
        val hashtags =
            list
                .flatMap { cs ->
                    val fromTag =
                        cs.tags["hashtags"]
                            .orEmpty()
                            .split(';')
                            .map { it.trim().lowercase() }
                            .filter { it.isNotEmpty() }
                            .map { if (it.startsWith("#")) it else "#$it" }
                    (fromTag + RowTags.hashtags(cs.comment)).distinct()
                }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }
        return UserActivity(
            sample = n,
            from = list.minOf { it.createdAt },
            to = list.maxOf { it.createdAt },
            activeDays =
                list.map { it.createdAt.atZone(ZoneOffset.UTC).toLocalDate() }.toSet().size,
            editors = editors,
            avgChanges = changes.sum() / n,
            maxChanges = changes.max(),
            noCommentPct = pct(list.count { it.comment.isNullOrBlank() }, n),
            hashtags = hashtags,
            inAreas =
                list.count { cs ->
                    val b = cs.bbox
                    b != null && areas.any { it.intersects(b) }
                },
        )
    }
}
