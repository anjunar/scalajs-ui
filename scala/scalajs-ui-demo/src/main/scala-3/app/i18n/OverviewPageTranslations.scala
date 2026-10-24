package app.i18n

import app.i18n.TranslationSupport.de
import ui.core.i18n.{CatalogEntry, i18n}

object OverviewPageTranslations {
  private val user  = ""
  private val group = ""

  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Welcome to Scala JS UI 1.0", "Willkommen bei Scala JS UI 1.0"),
    de(
      i18n"Reactive Scala.js interfaces with SSR and hydration built into the same component model.",
      "Reaktive Scala.js-Oberflächen mit SSR und Hydration im selben Komponentenmodell."
    ),
    de(i18n"Start with working code", "Mit funktionierendem Code beginnen"),
    de(
      i18n"A Property drives the text, and the event updates that same state after hydration.",
      "Eine Property steuert den Text und das Ereignis aktualisiert denselben Zustand nach der Hydration."
    ),
    de(i18n"SSR", "SSR"),
    de(
      i18n"Server HTML and client hydration share the same structure.",
      "Server-HTML und Client-Hydration verwenden dieselbe Struktur."
    ),
    de(i18n"DSL", "DSL"),
    de(
      i18n"Templates stay declarative and free of DOM handwork.",
      "Templates bleiben deklarativ und frei von manueller DOM-Arbeit."
    ),
    de(i18n"Live", "Live"),
    de(
      i18n"Every page shows a usable example instead of a dry API list.",
      "Jede Seite zeigt ein nutzbares Beispiel statt einer trockenen API-Liste."
    ),
    de(i18n"One component model", "Ein Komponentenmodell"),
    de(
      i18n"Render complete HTML on the server, then hydrate the same tree in the browser.",
      "Vollständiges HTML auf dem Server rendern und anschließend denselben Baum im Browser hydrieren."
    ),
    de(
      i18n"The declarative Scala DSL, reactive properties, router, forms, and lifecycle-aware components share one runtime. The TypeScript packages expose that runtime through a typed facade.",
      "Die deklarative Scala-DSL, reaktive Properties, Router, Formulare und lebenszyklusgebundene Komponenten teilen sich eine Runtime. Die TypeScript-Pakete stellen sie über eine typisierte Fassade bereit."
    ),
    de(i18n"Message-centered I18n", "Nachrichtenzentrierte i18n"),
    de(
      i18n"The English source lives in Scala code. The catalog attaches multiple languages to exactly that one message.",
      "Der englische Ausgangstext steht im Scala-Code. Der Katalog ordnet genau dieser einen Nachricht mehrere Sprachen zu."
    ),
    de(i18n"Switch locale", "Locale wechseln"),
    de(i18n"Delete document", "Dokument löschen"),
    de(i18n"User $user invited you to $group", "{user} hat dich zu {group} eingeladen"),
    de(
      i18n"Missing translations fall back to English",
      "Fehlende Übersetzungen fallen auf Englisch zurück"
    ),
    de(i18n"01", "01"),
    de(i18n"Readability first", "Lesbarkeit zuerst"),
    de(
      i18n"Components are shown so their purpose, state, and placement are immediately clear.",
      "Komponenten werden so dargestellt, dass ihr Zweck, Zustand und ihre Platzierung sofort klar sind."
    ),
    de(i18n"02", "02"),
    de(i18n"Hydration in view", "Hydration im Blick"),
    de(
      i18n"Examples avoid hidden DOM drift and keep virtual containers understandable.",
      "Beispiele vermeiden verborgene DOM-Abweichungen und halten virtuelle Container verständlich."
    ),
    de(i18n"03", "03"),
    de(i18n"A growing system", "Ein wachsendes System"),
    de(
      i18n"New components get room for context, variants, API, and architectural hints.",
      "Neue Komponenten erhalten Raum für Kontext, Varianten, API und Architekturhinweise."
    ),
    de(i18n"What you find on the component pages", "Was du auf den Komponentenseiten findest"),
    de(
      i18n"A short explanation of when the component makes sense.",
      "Eine kurze Erklärung, wann die Komponente sinnvoll ist."
    ),
    de(
      i18n"At least one real live state with data or interaction.",
      "Mindestens einen echten Live-Zustand mit Daten oder Interaktion."
    ),
    de(
      i18n"Concrete DSL examples that stay close to production code.",
      "Konkrete DSL-Beispiele, die nah am Produktivcode bleiben."
    ),
    de(
      i18n"Notes about stability, cursor behavior, SSR, or reactive properties.",
      "Hinweise zu Stabilität, Cursor-Verhalten, SSR oder reaktiven Properties."
    ),
    de(i18n"Next step", "Nächster Schritt"),
    de(
      i18n"Pick a component on the left. Each page is now denser and still leaves room for more building blocks without losing the thread.",
      "Wähle links eine Komponente aus. Jede Seite ist jetzt dichter und lässt dennoch Raum für weitere Bausteine, ohne den roten Faden zu verlieren."
    ),
    de(i18n"Source", "Quelle"),
    de(i18n"Resolved", "Aufgelöst")
  )
}
