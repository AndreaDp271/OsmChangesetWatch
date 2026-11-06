# Changelog

## 1.0.0 (versionCode 100)
- Aree di osservazione su mappa (osmdroid), con superficie in km² e avviso per aree grandi.
- Verifica geometrica dei changeset: solo quelli che toccano davvero l'area (CONFIRMED); gli altri dietro il filtro "Anche non verificati".
- Minimappa del changeset: nuovo (verde), modificato (arancione), eliminato (rosso tratteggiato); tocco su un elemento per vedere i tag cambiati.
- Minimappa a schermo intero (pulsante in alto a destra sulla mappa), con legenda e tocco sugli elementi.
- Elementi modificati: la forma precedente (grigio tratteggiato) resta visibile sotto quella nuova; i nodi spostati mostrano la vecchia posizione.
- Controllo in background (WorkManager) con notifiche raggruppate per area; intervallo da 15 min a 6 h.
- Impostazioni nello stile ParkMap: tema, colori (blu o Material You), lingua (it/en), filtro, cache mappa.
- Italiano e inglese; nessun servizio proprietario, nessun tracker.

## Unreleased
- Search box in the list (user, comment, editor, id); "Only my areas" filter off = global search by OSM user name or changeset id.
- Opens openstreetmap.org/changeset/ID links from other apps and browsers.
- OSMCha-style flags (many changes, no comment, mass deletion, new mapper, previously blocked, suspect user) and trusted/suspect user lists.
- Filters in a bottom sheet: flagged only, hide trusted, size, date, editor, hashtag, type of change (from the changeset counts).
- Changeset detail: relations, full list of changed elements with tag diffs, history and "Edit in iD" links, discussion.
- OSM login (OAuth2 PKCE, no secret in the app) to comment on changesets; token encrypted with the Android Keystore.
- Author card with profile numbers and "Recent activity" (HDYC-like summary of the last 100 changesets).
- Watch OSM users anywhere in the world, not only areas.
- Area editor: search municipalities and places with Nominatim; an area can follow the real boundary, and verification uses it.
- Redesigned list, detail, areas and area editor; footprint glyph showing where a changeset falls in the area.
- Database migrations keep existing areas (versions 2 to 5).
- "Load more" at the end of the list fetches older changesets; the verification budget now counts only real downloads.
- Element history inside the app, with the differences between versions and links to each changeset.
- Maps zoom up to 21; they open at most at zoom 19, where OSM tiles exist, and enlarge those beyond it (zoom 29 was unreadable).
- Relative times ("4 hours ago") follow the app language instead of the system language.
