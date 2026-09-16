package app.i18n

import app.i18n.TranslationSupport.de
import ui.core.i18n.{CatalogEntry, i18n}

object TableViewPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Author ascending, year descending", "Autor aufsteigend, Jahr absteigend"),
    de(i18n"Reload current sorting", "Aktuelle Sortierung neu laden"),
    de(
      i18n"Shift-click headers to sort by multiple columns. Enter or Space sorts a focused header; Shift keeps other sort columns.",
      "Shift-Klick auf Spaltenköpfe sortiert nach mehreren Spalten. Enter oder Leertaste sortiert den fokussierten Spaltenkopf; Shift behält andere Sortierspalten bei."
    ),
    de(i18n"TableView", "TableView"),
    de(
      i18n"Reactive rows with a stable SSR and hydration structure.",
      "Reaktive Zeilen mit einer stabilen SSR- und Hydration-Struktur."
    ),
    de(i18n"Data view", "Datenansicht"),
    de(
      i18n"A table should keep changing data calm.",
      "Eine Tabelle sollte auch veränderliche Daten ruhig darstellen."
    ),
    de(
      i18n"A generated in-memory data source exposes 1,000 rows through RemoteListProperty. The table requests only the visible ranges.",
      "Eine generierte In-Memory-Datenquelle stellt über RemoteListProperty 1.000 Zeilen bereit. Die Tabelle fordert nur die sichtbaren Bereiche an."
    ),
    de(i18n"Remote in-memory book table", "Entfernte In-Memory-Buchtabelle"),
    de(
      i18n"Grouped columns, row and cell selection, resizing, reordering and direction over a remote source.",
      "Gruppierte Spalten, Zeilen- und Zellauswahl, Größenänderung, Umsortierung und Leserichtung über einer entfernten Quelle."
    ),
    de(i18n"50 initial rows · 1,000 total", "50 initiale Zeilen · 1.000 insgesamt"),
    de(
      i18n"Scroll through remote ranges or sort any column; the table keeps one stable virtual surface.",
      "Scrolle durch entfernte Bereiche oder sortiere eine Spalte; die Tabelle behält eine stabile virtuelle Fläche."
    ),
    de(
      i18n"This content header scrolls with the rows while the column header stays fixed.",
      "Dieser Inhalts-Header scrollt mit den Zeilen, während der Spalten-Header fixiert bleibt."
    ),
    de(i18n"Loading generated books...", "Generierte Bücher werden geladen …"),
    de(i18n"Memory", "Speicher"),
    de(i18n"The source stays local", "Die Quelle bleibt lokal"),
    de(
      i18n"A deterministic catalog generates 1,000 rows without a server or network request.",
      "Ein deterministischer Katalog erzeugt 1.000 Zeilen ohne Server oder Netzwerkanfrage."
    ),
    de(i18n"SSR", "SSR"),
    de(i18n"Initial structure is deterministic", "Die initiale Struktur ist deterministisch"),
    de(
      i18n"Configuration runs before dynamic row and column mount points are created.",
      "Die Konfiguration läuft, bevor dynamische Mount-Punkte für Zeilen und Spalten erstellt werden."
    ),
    de(i18n"Remote", "Remote"),
    de(i18n"Large sources remain lazy", "Große Quellen bleiben lazy"),
    de(
      i18n"RemoteListProperty exposes range loading, placeholders, and sortable query state.",
      "RemoteListProperty stellt das Laden von Bereichen, Platzhalter und einen sortierbaren Abfragezustand bereit."
    ),
    de(i18n"Table DSL", "Tabellen-DSL"),
    de(
      i18n"Columns keep their renderer next to the data they display.",
      "Spalten halten ihren Renderer direkt bei den Daten, die sie darstellen."
    ),
    de(i18n"In-memory RemoteListProperty", "In-Memory-RemoteListProperty"),
    de(
      i18n"The loader slices and sorts one generated Vector.",
      "Der Loader schneidet und sortiert einen generierten Vector."
    ),
    // Readout tiles
    de(i18n"Rows loaded", "Geladene Zeilen"),
    de(i18n"Selection mode", "Auswahlmodus"),
    de(i18n"Selection target", "Auswahlziel"),
    de(i18n"Selected rows", "Ausgewählte Zeilen"),
    de(i18n"Selected cells", "Ausgewählte Zellen"),
    de(i18n"Focused row", "Fokussierte Zeile"),
    de(i18n"Last double-click", "Letzter Doppelklick"),
    de(i18n"Last commit", "Letzte Übernahme"),
    // Control groups
    de(i18n"Sorting", "Sortierung"),
    de(i18n"Clear sorting", "Sortierung aufheben"),
    de(i18n"Selection", "Auswahl"),
    de(
      i18n"Ctrl/Cmd-click toggles rows; Shift-click selects a range.",
      "Ctrl/Cmd-Klick wählt Zeilen an oder ab; Shift-Klick wählt einen Bereich."
    ),
    de(
      i18n"In cell mode, Shift-click and Shift+Arrow select an inclusive rectangle.",
      "Im Zellmodus wählen Shift-Klick und Shift+Pfeil einen einschließenden Rechteckbereich."
    ),
    de(i18n"Toggle single / multiple selection", "Einzel-/Mehrfachauswahl umschalten"),
    de(i18n"Toggle row / cell selection", "Zeilen-/Zellauswahl umschalten"),
    de(i18n"Clear book selection", "Buchauswahl aufheben"),
    de(i18n"Columns", "Spalten"),
    de(
      i18n"Drag a column edge to resize. Focus its grip and use arrow keys for keyboard resizing.",
      "Ziehe am Spaltenrand, um die Breite zu ändern. Fokussiere den Ziehgriff und nutze die Pfeiltasten für die Tastaturbedienung."
    ),
    de(
      i18n"Drag a column header to move it. Or focus the header and press Alt+Shift+Left/Right.",
      "Ziehe einen Spaltenkopf zum Verschieben. Oder fokussiere ihn und drücke Alt+Shift+Links/Rechts."
    ),
    de(
      i18n"The column menu button in the header corner hides and shows individual columns.",
      "Der Spaltenmenü-Button in der Header-Ecke blendet einzelne Spalten aus und wieder ein."
    ),
    de(i18n"Toggle constrained / free column widths", "Angepasste/freie Spaltenbreiten umschalten"),
    de(
      i18n"Toggle left-to-right / right-to-left",
      "Links-nach-rechts/Rechts-nach-links umschalten"
    ),
    de(i18n"Navigation", "Navigation"),
    de(
      i18n"For horizontal navigation, use free widths and widen the columns.",
      "Für horizontale Navigation auf freie Breiten umschalten und die Spalten verbreitern."
    ),
    de(i18n"Go to row 500", "Zu Zeile 500 springen"),
    de(i18n"Go to first row", "Zur ersten Zeile springen"),
    de(i18n"Show selected row", "Ausgewählte Zeile anzeigen"),
    // Editing showcase
    de(i18n"Editable cells", "Editierbare Zellen"),
    de(
      i18n"Standard cells edit the property the column was given: text, check box, choice box, converting text and a read-only progress bar.",
      "Standardzellen bearbeiten die Property, die der Spalte übergeben wurde: Text, Kontrollkästchen, Auswahlfeld, konvertierender Text und ein schreibgeschützter Fortschrittsbalken."
    ),
    de(i18n"Editing", "Bearbeiten"),
    de(
      i18n"Double-click a cell, or press Enter on a focused one, to start editing. Enter commits, Escape cancels.",
      "Doppelklicke eine Zelle oder drücke Enter auf der fokussierten Zelle, um die Bearbeitung zu starten. Enter übernimmt, Escape bricht ab."
    ),
    de(
      i18n"The converting rating cell rejects anything outside 1 to 5 instead of writing it back.",
      "Die konvertierende Bewertungszelle weist alles außerhalb von 1 bis 5 ab, statt es zurückzuschreiben."
    ),
    de(i18n"Reset rows", "Zeilen zurücksetzen"),
    de(i18n"Editable standard cells", "Editierbare Standardzellen"),
    de(
      i18n"A standard cell edits the property its column resolved for the row.",
      "Eine Standardzelle bearbeitet die Property, die ihre Spalte für die Zeile aufgelöst hat."
    )
  )
}
