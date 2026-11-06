# PLAN

Obiettivo: app Android OSM Changeset Watch (spec: vedi richiesta iniziale). Lavoro per fasi.

## Stato
- [x] Fase 1 — scheletro e rete (completata, vedi sotto)
- [ ] Fase 2 — aree e persistenza (Room, mappa, editor area)
- [ ] Fasi 3-6

## Decisioni
- Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 26.
- API verificata (wiki API v0.6): `bbox=W,S,E,N`; `from=T1[&to=T2]` filtra per data di CREAZIONE (usato);
  `time=T1` filtra per CHIUSURA (non usato); `limit` max 100; ordine default newest; `order=oldest` non combinabile con `time`.
  Paginazione (fase 4): ripetere con `to=` = created_at dell'ultimo ricevuto.
- User-Agent in `OsmConfig.USER_AGENT` (URL contatto da sostituire: CHANGEME).
- Test JVM del parser usano kxml2+xmlpull solo come testImplementation.
- applicationId provvisorio `org.osmwatch.changesetwatch`; licenza e soglie filtro: domande aperte.

## Test
- `./gradlew.bat testDebugUnitTest` (9 test, passano)
- Chiamata reale: `OSM_LIVE=1 ./gradlew.bat testDebugUnitTest --tests "*liveCall*" -i`
- APK: `./gradlew.bat assembleDebug`

## Da verificare
- Nodi cancellati nel osmChange: coordinate presenti o no? (il parser le accetta null; fixture `osmchange_delete.xml` è scritta a mano)

## Prossimo passo
Fase 2: scegliere mappa (MapLibre vs osmdroid), Room, editor area.
