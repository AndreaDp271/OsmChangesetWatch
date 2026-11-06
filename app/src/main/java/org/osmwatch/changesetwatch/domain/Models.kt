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
    /** Dall'intestazione (`created_count` ecc.): null se il server non li fornisce. */
    val createdCount: Int? = null,
    val modifiedCount: Int? = null,
    val deletedCount: Int? = null,
) {
    val comment: String? get() = tags["comment"]
    val createdBy: String? get() = tags["created_by"]
    val source: String? get() = tags["source"]
}

enum class ChangeAction { CREATE, MODIFY, DELETE }

/** PENDING = non ancora verificato; gli altri sono l'esito della verifica geometrica (vedi [ChangesetVerifier]). */
enum class Verdict { PENDING, CONFIRMED, LIKELY, UNCERTAIN, OUTSIDE, SKIPPED_TOO_LARGE }

enum class ElementType { NODE, WAY, RELATION }

/** Membro di una relation: [type] è "node", "way" o "relation". */
data class Member(val type: String, val ref: Long, val role: String)

data class ElementRef(val type: ElementType, val id: Long, val version: Int?)

/** Stato di un elemento OSM (nodo: [at]; way: [refs]). */
data class ElementData(val at: LatLon?, val refs: List<Long>, val tags: Map<String, String>, val members: List<Member> = emptyList())

/** lat/lon null: i nodi cancellati nel osmChange non hanno coordinate (verificato sull'API). */
data class ChangedNode(
    val id: Long, val action: ChangeAction, val lat: Double?, val lon: Double?, val version: Int? = null,
    val tags: Map<String, String> = emptyMap(),
)

data class ChangedWay(
    val id: Long, val action: ChangeAction, val version: Int?, val nodeRefs: List<Long>,
    val tags: Map<String, String> = emptyMap(),
)

data class ChangedRelation(
    val id: Long, val action: ChangeAction, val version: Int?, val members: List<Member>,
    val tags: Map<String, String> = emptyMap(),
)

data class OsmChangeSummary(
    val nodes: List<ChangedNode>,
    val ways: List<ChangedWay>,
    val relationCount: Int,
    val relations: List<ChangedRelation> = emptyList(),
) {
    val wayCount: Int get() = ways.size
}

/** Commento nella discussione di un changeset (solo quelli visibili). */
data class ChangesetComment(val user: String?, val date: Instant?, val text: String)

/** Profilo pubblico OSM dell'autore (`GET /user/ID`); i campi mancanti sono null. */
data class UserInfo(
    val name: String?, val createdAt: Instant?, val changesets: Int?, val blocksReceived: Int?,
    val roles: List<String> = emptyList(), val traces: Int? = null,
)
