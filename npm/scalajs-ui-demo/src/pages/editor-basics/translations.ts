import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/scalajs-ui-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Editor`, { de: "Editor" }),
  catalogEntry(i18n`Getting started`, { de: "Erste Schritte" }),
  catalogEntry(i18n`plugins picks toolbar capabilities by name; an editor without plugin configuration shows the standard toolbar. The link and image dialogs open as @anjunar/scalajs-ui-viewport windows, so the editor needs a viewport ancestor -- entry-client.ts/entry-server.ts already wrap the whole app in one. onSession lends the live Ember session: typed commands such as undo run against it, and createEditor() makes the same kind of session without any DOM.`, { de: "plugins wählt Toolbar-Fähigkeiten per Namen; ohne Plugin-Konfiguration erscheint die Standard-Toolbar. Link- und Bilddialoge öffnen sich als @anjunar/scalajs-ui-viewport-Fenster, daher braucht der Editor einen Viewport-Vorfahren – entry-client.ts und entry-server.ts umschließen die gesamte Anwendung bereits damit. onSession leiht die laufende Ember-Session aus: typisierte Befehle wie undo laufen direkt darauf, und createEditor() erzeugt dieselbe Art Session ganz ohne DOM." }),
  catalogEntry(i18n`An Ember-based rich-text field with Markdown output, a complete toolbar, and live value feedback.`, { de: "Ein Ember-basierter Rich-Text-Editor mit Markdown-Ausgabe, vollständiger Toolbar und Live-Wertanzeige." }),
  catalogEntry(i18n`editor(), plugins: a model-bound Ember editor whose public Markdown value remains observable and replaceable.`, { de: "editor(), plugins: ein modellgebundener Ember-Editor, dessen öffentlicher Markdown-Wert beobachtbar und austauschbar bleibt." }),
  catalogEntry(i18n`Load article`, { de: "Artikel laden" }),
  catalogEntry(i18n`Readonly`, { de: "Schreibgeschützt" }),
  catalogEntry(i18n`Markdown`, { de: "Markdown" }),
  catalogEntry(i18n`Clear editor`, { de: "Editor leeren" }),
  catalogEntry(i18n`Undo last change`, { de: "Letzte Änderung rückgängig" }),
  catalogEntry(i18n`Markdown value`, { de: "Markdown-Wert" }),
  catalogEntry(i18n`characters`, { de: "Zeichen" }),
];
