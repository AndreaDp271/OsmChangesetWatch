package org.osmwatch.changesetwatch.domain

/** Template dei visualizzatori esterni; `{id}` = id changeset. Configurabili qui in un unico punto. */
object Links {
    const val OSM = "https://www.openstreetmap.org/changeset/{id}"
    const val ACHAVI = "https://overpass-api.de/achavi/?changeset={id}"
    const val OSMCHA = "https://osmcha.org/changesets/{id}"

    private fun path(t: ElementType) = t.name.lowercase()

    /** Pagina dell'elemento con la cronologia (da cui si vede e si può annullare una modifica). */
    fun elementHistory(t: ElementType, id: Long) = "https://www.openstreetmap.org/${path(t)}/$id/history"

    fun editInId(t: ElementType, id: Long) = "https://www.openstreetmap.org/edit?editor=id&${path(t)}=$id"

    fun url(template: String, changesetId: Long) = template.replace("{id}", changesetId.toString())

    /** Id da un link `.../changeset/123` (o `/changesets/123`, anche con coda `/...` o `?...`), altrimenti null. */
    fun changesetId(link: String): Long? =
        Regex("""/changesets?/(\d{1,18})(?:[/?#]|$)""").find(link)?.groupValues?.get(1)?.toLongOrNull()
}
