- Immer `sbt` verwenden, niemals `sbtn`.
- Projekte liegen in Nachbarverzeichnissen. Bei Bedarf dort nachsehen.
- Keine Workarounds. Ursachen sauber verstehen und richtig lösen.
- Keine kompilierten JavaScript-Sourcen durchsuchen oder bearbeiten.
- Vor größeren Änderungen erst Architektur und bestehende Muster prüfen.

## Tests

Der Build läuft auf sbt 2. Dort delegiert `test` auf `testQuick`: es werden nur
Suites ausgeführt, die seit dem letzten grünen Lauf betroffen waren. `sbt --server test`
meldet deshalb regelmäßig „No tests to run" und ist als Abnahme-Gate untauglich.
`sbt --server clean` hilft nicht — der Action-Cache liegt außerhalb von `target/`.

Vollständiger Lauf über alle Module:

```
sbt --server "Test/testOnly *"
```

Das ist der Befehl, der vor einem Commit grün sein muss (aktuell 287 Tests).
`sbt --server test` ist für die schnelle Schleife während der Arbeit gedacht.

Für die npm-Seite ist `npm run verify --workspaces --if-present` das Äquivalent —
pro Paket Typecheck, Testsuite und Tarball-Consumer-Test, für die Demo die
Client-/SSR-Builds samt Eine-Runtime-Nachweis. Braucht vorher
`sbt --server "scalajs-ui-bridge/fullLinkJS"` und die TypeScript-Deklarationen
in Abhängigkeitsreihenfolge (`npm run build --workspace npm/scalajs-ui-core`,
dann router, viewport, controls, forms, editor, json, webauthn). Die
Playwright-Suite in `npm/scalajs-ui-core-browser-tests` braucht zusätzlich
`scalajs-ui-core-browser-tests/fullLinkJS` und `scalajs-ui-editor-browser-tests/fullLinkJS`.

`npm run check-version -- <Version aus build.sbt>` prüft, dass jede Versionsangabe
mit `build.sbt` übereinstimmt; `npm run check:i18n` prüft, dass jede Demo-Nachricht
eine deutsche Übersetzung hat. Beides läuft auch in CI.

Scalafix (`.scalafix.conf`, Regel `tools/scalafix/ShortenQualifiedNames.scala`) ist
nicht in den Build eingebunden; die Regel wird bei Bedarf manuell über die
Scalafix-CLI angewendet.

`.github/workflows/verify.yml` fährt beide Gates bei jedem Push nach `master`
und jedem Pull Request.
