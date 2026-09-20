package app.i18n

import app.i18n.TranslationSupport.de
import ui.core.i18n.{CatalogEntry, i18n}

object EditorPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Editor", "Editor"),
    de(
      i18n"Markdown as the stable editor value in SSR and the browser.",
      "Markdown als stabiler Editor-Wert in SSR und Browser."
    ),
    de(i18n"Structured content", "Strukturierte Inhalte"),
    de(i18n"One Markdown value", "Ein Markdown-Wert"),
    de(
      i18n"The demo starts writable. Markdown source and readonly presentation are explicit modes outside the editor.",
      "Die Demo startet schreibbar. Markdown-Quelle und schreibgeschützte Darstellung sind explizite Modi außerhalb des Editors."
    ),
    de(i18n"Full editor", "Vollständiger Editor"),
    de(
      i18n"Formatting, headings, lists, links, images, tables, code and horizontal rules are independent plugins.",
      "Formatierung, Überschriften, Listen, Links, Bilder, Tabellen, Code und Trennlinien sind unabhängige Plugins."
    ),
    de(i18n"Write the article...", "Artikel schreiben …"),
    de(i18n"Readonly", "Schreibgeschützt"),
    de(i18n"Markdown", "Markdown"),
    de(i18n"Readonly SSR preview", "Schreibgeschützte SSR-Vorschau"),
    de(
      i18n"The same Markdown value remains meaningful before JavaScript starts and when editing is disabled.",
      "Derselbe Markdown-Wert bleibt vor dem JavaScript-Start und bei deaktivierter Bearbeitung aussagekräftig."
    ),
    de(i18n"Contextual plugin DSL", "Kontextbezogene Plugin-DSL"),
    de(
      i18n"Install only the editing capabilities needed by a field; the value remains Markdown.",
      "Installiere nur die für ein Feld benötigten Bearbeitungsfunktionen; der Wert bleibt Markdown."
    )
  )
}
