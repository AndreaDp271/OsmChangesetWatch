package org.osmwatch.changesetwatch.domain

import java.time.Instant

data class BBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    /** Formato API OSM: min_lon,min_lat,max_lon,max_lat (W,S,E,N). */
    fun toApiParam(): String = "$minLon,$minLat,$maxLon,$maxLat"
}

data class Changeset(
    val id: Long,
    val user: String?,
    val uid: Long?,
    val createdAt: Instant,
    val closedAt: Instant?, // null se ancora aperto
    val open: Boolean,
    val bbox: BBox?, // null se il changeset non ha modifiche
    val changesCount: Int?,
    val commentsCount: Int?,
    val tags: Map<String, String>,
) {
    val comment: String? get() = tags["comment"]
    val createdBy: String? get() = tags["created_by"]
    val source: String? get() = tags["source"]
}

enum class ChangeAction { CREATE, MODIFY, DELETE }

/** lat/lon null: es. nodi cancellati, che l'API può restituire senza coordinate. */
data class ChangedNode(val id: Long, val action: ChangeAction, val lat: Double?, val lon: Double?)

data class OsmChangeSummary(
    val nodes: List<ChangedNode>,
    val wayCount: Int,
    val relationCount: Int,
)
