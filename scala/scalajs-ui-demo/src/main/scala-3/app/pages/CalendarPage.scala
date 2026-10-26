package app.pages

import app.components.Showcase.*
import ui.control.calendar.{Calendar, CalendarFormat}
import ui.control.calendar.Calendar.*
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.StyleDsl.*
import ui.core.i18n.{I18nRuntime, i18n}
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.Cursor
import ui.core.state.Property

import java.time.{DayOfWeek, LocalDate}

object CalendarPage {
  def render()(using AbstractComponent, Cursor): Unit =
    showcasePage(i18n"Calendar", i18n"A month to look at and a day to choose.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Dates",
          i18n"The calendar shows six weeks and lets the visitor choose a day.",
          i18n"Which days can be chosen and which carry a mark is the application's decision. Names come from the platform in the page's language; the grid follows the WAI-ARIA date picker pattern."
        )

        componentShowcase(
          i18n"Choose a day",
          i18n"Arrow keys move by a day or a week, Page Up and Page Down by a month, with Shift by a year."
        ) {
          val chosen = Property(Option.empty[LocalDate])
          vbox {
            style { gap = "14px" }
            localized(calendar(chosen) {})
            div {
              classes = Seq("showcase-action-row")
              text(chosen.map(_.map(_.toString).getOrElse("–"))) {}
            }
          }
        }

        componentShowcase(
          i18n"Bookable days",
          i18n"Only the next six weeks, no weekends; days with free times carry a mark."
        ) {
          val today  = Calendar.localToday()
          val chosen = Property(Option.empty[LocalDate])
          localized(calendar(chosen) {
            minDate = today
            maxDate = today.plusWeeks(6)
            enabled = (date: LocalDate) => date.getDayOfWeek.getValue <= DayOfWeek.FRIDAY.getValue
            marked = (date: LocalDate) =>
              date.getDayOfWeek == DayOfWeek.TUESDAY || date.getDayOfWeek == DayOfWeek.THURSDAY
          })
        }

        apiSection(
          i18n"Contextual DSL",
          i18n"The chosen day is a property of the application; bounds and rules are plain values or properties."
        ) {
          codeBlock(
            "scala",
            """val chosen = Property(Option.empty[LocalDate])

calendar(chosen) {
  locale = "de"
  minDate = LocalDate.now()
  enabled = (date: LocalDate) => freeDays.contains(date)
  marked = (date: LocalDate) => freeDays.contains(date)
}"""
          )
        }
      }
    }

  /** Follows the page's language with names and button labels. */
  private def localized(calendar: Calendar)(using AbstractComponent): Unit = {
    val runtime = I18nRuntime.require
    calendar.addDisposable(runtime.locale.observe { value =>
      calendar.localeProperty.set(value.code)
      calendar.formatProperty.set(CalendarFormat.intl(value.code))
    })
    calendar.addDisposable(
      runtime.text(i18n"Previous month").observe(calendar.previousMonthLabelProperty.set)
    )
    calendar.addDisposable(
      runtime.text(i18n"Next month").observe(calendar.nextMonthLabelProperty.set)
    )
  }
}
