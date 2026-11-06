package org.osmwatch.changesetwatch.network

object OsmConfig {
    const val BASE_URL = "https://api.openstreetmap.org/api/0.6"

    /** Sito web: solo per ciò che l'API non espone (gli id dei blocchi ricevuti da un utente). */
    const val WEB_URL = "https://www.openstreetmap.org"

    /**
     * Obbligatorio per policy OSM: identifica l'app e un contatto. Tenere la versione allineata a
     * versionName.
     */
    const val REPO = "https://github.com/AndreaDp271/OsmChangesetWatch"
    const val USER_AGENT = "OSMChangesetWatch/1.0.0 ($REPO)"

    const val CONNECT_TIMEOUT_S = 15L
    const val READ_TIMEOUT_S = 30L
    const val MAX_RETRIES = 4
}
