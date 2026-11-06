package org.osmwatch.changesetwatch.domain

/** Template dei visualizzatori esterni; `{id}` = id changeset. Configurabili qui in un unico punto. */
object Links {
    const val OSM = "https://www.openstreetmap.org/changeset/{id}"
    const val ACHAVI = "https://overpass-api.de/achavi/?changeset={id}"
    const val OSMCHA = "https://osmcha.org/changesets/{id}"

    fun url(template: String, changesetId: Long) = template.replace("{id}", changesetId.toString())

    /** Id da un link `.../changeset/123` (o `/changesets/123`, anche con coda `/...` o `?...`), altrimenti null. */
    fun changesetId(link: String): Long? =
        Regex("""/changesets?/(\d{1,18})(?:[/?#]|$)""").find(link)?.groupValues?.get(1)?.toLongOrNull()
}
