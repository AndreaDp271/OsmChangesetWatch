# PLAN

Obiettivo: app Android OSM Changeset Watch (spec: vedi richiesta iniziale). Lavoro per fasi.

## Stato
- [x] Fase 1 — scheletro e rete (completata, vedi sotto)
- [x] Fase 2 — verificata su telefono (CPH2363): crea/modifica/disabilita/elimina, dati persistenti al riavvio
- [ ] Fasi 3-6

## Decisioni
- Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 26.
- API verificata (wiki API v0.6): `bbox=W,S,E,N`; `from=T1[&to=T2]` filtra per data di CREAZIONE (usato);
  `time=T1` filtra per CHIUSURA (non usato); `limit` max 100; ordine default newest; `order=oldest` non combinabile con `time`.
  Paginazione (fase 4): ripetere con `to=` = created_at dell'ultimo ricevuto.
- User-Agent in `OsmConfig.USER_AGENT` (URL contatto da sostituire: CHANGEME).
- Test JVM del parser usano kxml2+xmlpull solo come testImplementation.
- Refresh manuale: ultimi 100 changeset per area SENZA `from` (anche precedenti alla creazione area, richiesta autore). `from`+paginazione solo in fase 4.
- Stile condiviso con ParkMap (repo AndreaDp271/Park_finder-APK): Theme.kt (stessa palette blu), Common.kt (Bar/Group/Item/Choice), settings con tema/palette/lingua. Icone material-icons-extended (R8 in release in fase 6). Updater GitHub NON portato (F-Droid).
- Background: `time=T1[,T2]` (chiusi dopo T1 o aperti) + paginazione T2=created_at più vecchio+1s, max 5 pagine; primo giro = seed senza notifica; notifica 1 per area (id = areaId).
- Mappa: osmdroid 6.1.20 (scelta confermata dall autore). Room 2.6.1 + KSP.
- applicationId `io.github.andreadp271.osmwatch` (scelto dall autore, coerente con ParkMap); namespace/package Kotlin resta org.osmwatch.changesetwatch. Licenza e soglie filtro: domande aperte.

## Test
- `./gradlew.bat testDebugUnitTest` (9 test, passano)
- Chiamata reale: `OSM_LIVE=1 ./gradlew.bat testDebugUnitTest --tests "*liveCall*" -i`
- APK: `./gradlew.bat assembleDebug`

## Da verificare
- Nodi cancellati nel osmChange: coordinate presenti o no? (il parser le accetta null; fixture `osmchange_delete.xml` è scritta a mano)

## Prossimo passo
Fase 5: filtro anti-falso-positivo (pipeline + test). Soglie da concordare (domanda aperta #3).

## Minimappa (richiesta autore, in deroga al non-obiettivo "nessun visualizzatore diff interno")
- Nel dettaglio: osmChange + multi-fetch (`nodes?nodes=idvN`, `ways?ways=idvN`, blocchi da 100, pausa 500 ms, max 700 id extra).
- Verificato sull'API: i nodi cancellati NON hanno coordinate nel osmChange -> si usa la versione v-1. Le way non hanno coordinate -> nodi dal osmChange, gli altri alla posizione corrente.
- Colori: nuovo verde pieno, modificato arancione, eliminato rosso tratteggiato/cerchio vuoto. Relation non disegnate; >2000 modifiche: non scarica.
- Test: ChangeGeometryTest (resolver con sorgente finta), live: `OSM_LIVE=1 OSM_CS=<id> gradlew testDebugUnitTest --tests "*LiveGeometryTest*" -i`.
