# Keyed-Reconciliation: große Permutationen

Historischer Kandidatenstand vom 14. September 2026: Korrektur im UI-Core, geprüft im Editor
mit der nur lokal veröffentlichten Version `1.0.1-p28-SNAPSHOT`.
Es wurde keine Releaseversion veröffentlicht oder im normalen Editor-Build umgestellt.

## Vertrag

`KeyedChildren` aktualisiert erhaltene Werte, entfernt fehlende Einträge und
mountet neue physische Elementkomponenten am Gruppenende. Danach übergibt es
die gewünschte vollständige Kindpermutation an `Runtime.reorderChildren`.
Diese `private[ui]`-Methode ist ein internes Runtime-Werkzeug, keine öffentliche
Transfer-API. Sie akzeptiert jedes bestehende Kind genau einmal, ohne fremde,
doppelte, virtuelle oder reine Textkinder. Reparenting bleibt bei `Runtime.move`.

Runtime besitzt weiterhin die einzige dauerhafte Kinderliste. Eine längste
steigende Teilfolge (LIS) der bisherigen Indizes bleibt stehen; die anderen
Hosts werden von rechts nach links vor den nächsten gewünschten Host oder den
virtuellen Endanker gesetzt. Die Planung benötigt O(n log n) Zeit und O(n)
Speicher. Sie führt n−LIS Inserts aus, insbesondere einen bei der Rotation eines
Kindes vom Anfang ans Ende. Backendkosten kommen hinzu: SSR-Array-Inserts können
weiterhin lineare Einzelkosten haben. Eine unveränderte Einzelposition wird auch
in `Runtime.move` vor der bisherigen Listenallokation erkannt.

Vor den Inserts werden Zielschreibrecht, Removal-Guards und Renderkontexte der
bewegten Komponenten geprüft. Parent, Mount-Parent, Instanzen und Lifecycle
bleiben bei derselben Ownership unverändert. Temporäre Indexverknüpfungen
vermerken jeden erfolgreichen Insert in konstanter Zeit. In `finally` wird
daraus die Runtime-Kinderliste einmal aktualisiert. Lehnt das Backend einen
späteren Insert vor dessen Mutation ab, bleiben logischer und physischer Baum
konsistent; ein Retry vervollständigt die gewünschte Reihenfolge.

Factory-/Update-Callbacks sind weiterhin nicht transaktional. Zwischen diesen
Callbacks ist noch nicht die vollständig gewünschte Reihenfolge hergestellt;
nach erfolgreichem Reconcile ist sie verbindlich. Es gibt keinen zweiten
Renderer und keine zusätzliche persistente Ownership-Struktur.

## Nachweise

`KeyedReorderSpec` ergänzt sieben Tests: Rotationen mit 500 und 50000 Kindern
(ein Insert, unverändert null), 100 deterministische Permutationen mit einer
unabhängigen quadratischen LIS-Referenz, gemischtes Löschen/Einfügen und
virtuelle Endgrenze, Guards ohne vorzeitige Mutation, partieller Backendfehler
mit konsistentem Baum und Retry sowie ungültige Permutationen.

```powershell
sbt --server "scalajs-ui-core/Test/testOnly *KeyedReorderSpec *HostEditingSpec"
sbt --server "Test/testOnly *"
sbt --server "scalajs-ui-bridge/fullLinkJS" "scalajs-ui-core-browser-tests/fullLinkJS"
npm run verify --workspace npm/scalajs-ui-core
npm run verify --workspace npm/scalajs-ui-demo
$env:EMBER_FIREFOX_CHANNEL = 'moz-firefox'
npm run test --workspace npm/scalajs-ui-core-browser-tests
```

Der optionale Firefox-Kanal entspricht dem Editor-Harness und umgeht lokal den
Startfehler des gebündelten Windows-Firefox. Ohne Variable bleibt der gebündelte
Browser voreingestellt; die CI-Konfiguration ändert sich nicht.

Geprüft: 22 gezielte Tests, insgesamt 452 Scala-Tests, 57 UI-Browserfälle,
114 npm-Core-Tests und 8 Tarball-Consumer-Tests sowie Demo-Typecheck,
Client-/SSR-/Pages-Build und Eine-Runtime-Nachweis. Die Core-Formatchecks bestehen.
Der globale UI-Formatcheck meldete eine parallel geänderte `TableView.scala`;
vor einem gemeinsamen Commit muss dieses globale Gate erneut bestehen.

Der [Editor-Kandidatenbericht](../scalajs-ember/benchmarks/runtime-reorder.md)
enthält reproduzierbare Befehle und maschinenlesbare Belege mit Quellhashes.
50000 Absätze benötigen beim ersten-nach-hinten-Move jetzt 95–132 ms in den
drei Engines, mit genau einem DOM-Move und ohne Remount. Mit 1.0.0 brach Chromium
nach 240 Sekunden ab. Die Werte sind lokale Einzelmessungen, keine allgemeine
Latenzzusage. Das Editor-Gate umfasst 1266 Scala-Tests und 802 Browserpässe
zusätzlich zu zwei bekannten erwarteten Windows-WebKit-Clipboard-Fehlern.

Lokaler Build des geprüften Kandidaten (PowerShell):

```powershell
sbt --server 'set uiCore / version := \"1.0.1-p28-SNAPSHOT\"' "scalajs-ui-core/publishLocal"
```

Dieser Override verändert keine Build-/Paketversion im Repository und ersetzt
kein vorhandenes 1.0.0-Artefakt. Veröffentlichung, Consumer-Dependency-Umstellung
und die noch offenen physischen Editor-Geräteabnahmen sind separate Schritte.

## Veröffentlichung als 1.0.1

Am 14. September 2026 wurde die Korrektur mit allen neun UI-Maven-Modulen als
1.0.1 veröffentlicht. Sonatype bestätigt Deployment
`dc3942f5-2142-48a5-a69d-83d4eadbce34` mit `PUBLISHED` und ohne Fehler.
Alle neun von Maven Central heruntergeladenen JARs stimmen per SHA-256 mit dem
geprüften und signierten Staging überein; die Core-Sourcen wurden vor dem Upload
bytegenau gegen den geprüften Arbeitsbaum verglichen.

Der Release besteht 454 Scala-Tests, den globalen Formatcheck, beide Production-
Links und alle npm-Workspace-Verifies einschließlich 57 Browserfällen. Der Editor
verwendet nun regulär UI-Core 1.0.1; der Snapshot oben bleibt als historische
Reproduktion erhalten. Der [Release-Nachweis](../scalajs-ember/benchmarks/ui-core-1.0.1-release.md)
enthält die Consumer-Prüfung, Artefakthashes und Sonatypes Kontingentwarnungen.