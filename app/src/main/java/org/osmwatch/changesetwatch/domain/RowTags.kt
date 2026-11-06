package org.osmwatch.changesetwatch.domain

/**
 * Editor e hashtag ricavati dai campi già salvati (created_by e commento): nessun dato in più nel
 * DB.
 */
object RowTags {
    private val HASHTAG = Regex("""#[\p{L}\p{N}_-]+""")

    /** "iD 2.30.1" -> "iD", "JOSM/1.5 (19396 it)" -> "JOSM"; null se created_by manca. */
    fun editor(createdBy: String?): String? =
        createdBy?.trim()?.takeIf { it.isNotEmpty() }?.split(' ', '/')?.first()

    /** Hashtag del commento in minuscolo, senza doppioni, nell'ordine di comparsa. */
    fun hashtags(comment: String?): List<String> =
        HASHTAG.findAll(comment.orEmpty()).map { it.value.lowercase() }.distinct().toList()
}
