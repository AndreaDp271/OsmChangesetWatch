package org.osmwatch.changesetwatch.domain

/** Template dei visualizzatori esterni; `{id}` = id changeset. Configurabili qui in un unico punto. */
object Links {
    const val OSM = "https://www.openstreetmap.org/changeset/{id}"
    const val ACHAVI = "https://overpass-api.de/achavi/?changeset={id}"
    const val OSMCHA = "https://osmcha.org/changesets/{id}"

    fun url(template: String, changesetId: Long) = template.replace("{id}", changesetId.toString())
}
