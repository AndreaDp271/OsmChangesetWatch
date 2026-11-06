package org.osmwatch.changesetwatch.domain

/** Filtro sul tipo di modifica, dai conteggi dell'intestazione. Changeset senza conteggi: esclusi quando il filtro è attivo. */
enum class ChangeKind {
    ANY, HAS_DELETES, ONLY_DELETES, ONLY_CREATES;

    fun matches(created: Int?, modified: Int?, deleted: Int?): Boolean {
        if (this == ANY) return true
        if (created == null || modified == null || deleted == null) return false
        return when (this) {
            ANY -> true
            HAS_DELETES -> deleted > 0
            ONLY_DELETES -> deleted > 0 && created == 0 && modified == 0
            ONLY_CREATES -> created > 0 && modified == 0 && deleted == 0
        }
    }
}
