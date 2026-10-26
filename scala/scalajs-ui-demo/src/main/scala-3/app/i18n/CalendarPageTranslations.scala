package app.i18n

import app.i18n.TranslationSupport.de
import ui.core.i18n.{CatalogEntry, i18n}

object CalendarPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Calendar", "Kalender"),
    de(
      i18n"A month to look at and a day to choose.",
      "Ein Monat zum Ansehen und ein Tag zum Wählen."
    ),
    de(i18n"Dates", "Daten"),
    de(
      i18n"The calendar shows six weeks and lets the visitor choose a day.",
      "Der Kalender zeigt sechs Wochen und lässt einen Tag wählen."
    ),
    de(
      i18n"Which days can be chosen and which carry a mark is the application's decision. Names come from the platform in the page's language; the grid follows the WAI-ARIA date picker pattern.",
      "Welche Tage wählbar sind und welche eine Markierung tragen, entscheidet die Anwendung. Die Namen kommen von der Plattform in der Sprache der Seite; das Raster folgt dem WAI-ARIA-Muster für Datumsauswahl."
    ),
    de(i18n"Choose a day", "Einen Tag wählen"),
    de(
      i18n"Arrow keys move by a day or a week, Page Up and Page Down by a month, with Shift by a year.",
      "Pfeiltasten bewegen um einen Tag oder eine Woche, Bild auf und Bild ab um einen Monat, mit Umschalt um ein Jahr."
    ),
    de(i18n"Bookable days", "Buchbare Tage"),
    de(
      i18n"Only the next six weeks, no weekends; days with free times carry a mark.",
      "Nur die nächsten sechs Wochen, keine Wochenenden; Tage mit freien Zeiten tragen eine Markierung."
    ),
    de(
      i18n"The chosen day is a property of the application; bounds and rules are plain values or properties.",
      "Der gewählte Tag ist eine Property der Anwendung; Grenzen und Regeln sind einfache Werte oder Properties."
    ),
    de(i18n"Previous month", "Vorheriger Monat"),
    de(i18n"Next month", "Nächster Monat"),
    de(i18n"Months, days and keyboard selection", "Monate, Tage und Auswahl per Tastatur")
  )
}
