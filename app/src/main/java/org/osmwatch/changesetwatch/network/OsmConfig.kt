package org.osmwatch.changesetwatch.network

object OsmConfig {
    const val BASE_URL = "https://api.openstreetmap.org/api/0.6"

    /** Obbligatorio per policy OSM: identifica l'app e un contatto. TODO: sostituire l'URL col repo reale. */
    const val USER_AGENT = "OSMChangesetWatch/0.1.0 (https://github.com/CHANGEME/osm-changeset-watch)"

    const val CONNECT_TIMEOUT_S = 15L
    const val READ_TIMEOUT_S = 30L
    const val MAX_RETRIES = 4
}
