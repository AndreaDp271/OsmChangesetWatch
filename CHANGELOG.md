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
