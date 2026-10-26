package ui.control.calendar

import java.time.{DayOfWeek, LocalDate, YearMonth}

import scala.scalajs.js

/** Names and labels of a calendar in one language, taken from the platform's `Intl.DateTimeFormat`.
  *
  * Dates are formatted at noon UTC with `timeZone: "UTC"`, so the visitor's own time zone can never
  * move a day to its neighbour. The server and the browser format with the same rules; an
  * application that needs other words replaces the format of its calendar.
  */
trait CalendarFormat {

  /** The heading of a month, e.g. "Oktober 2026". */
  def month(month: YearMonth): String

  /** The short name of a weekday in the column header, e.g. "Mo". */
  def weekdayShort(day: DayOfWeek): String

  /** The full name of a weekday, e.g. "Montag", for the header's accessible name. */
  def weekdayLong(day: DayOfWeek): String

  /** The full date a screen reader announces for a day, e.g. "Dienstag, 27. Oktober 2026". */
  def date(date: LocalDate): String
}

object CalendarFormat {

  /** The platform's names in a BCP 47 language such as "de" or "en-GB". */
  def intl(locale: String): CalendarFormat = new IntlCalendarFormat(locale)

  private final class IntlCalendarFormat(locale: String) extends CalendarFormat {
    private def formatter(options: js.Dynamic): js.Dynamic = {
      options.updateDynamic("timeZone")("UTC")
      js.Dynamic.newInstance(js.Dynamic.global.Intl.DateTimeFormat)(locale, options)
    }

    private val monthFormat  = formatter(js.Dynamic.literal(month = "long", year = "numeric"))
    private val shortWeekday = formatter(js.Dynamic.literal(weekday = "short"))
    private val longWeekday  = formatter(js.Dynamic.literal(weekday = "long"))
    private val longDate     =
      formatter(
        js.Dynamic.literal(weekday = "long", day = "numeric", month = "long", year = "numeric")
      )

    private def utcNoon(date: LocalDate): js.Date =
      new js.Date(js.Date.UTC(date.getYear, date.getMonthValue - 1, date.getDayOfMonth, 12))

    /** 1 January 2024 was a Monday; the days after it name every weekday once. */
    private def sample(day: DayOfWeek): LocalDate =
      LocalDate.of(2024, 1, 1).plusDays(day.getValue - 1L)

    override def month(month: YearMonth): String =
      monthFormat.format(utcNoon(month.atDay(1))).asInstanceOf[String]

    override def weekdayShort(day: DayOfWeek): String =
      shortWeekday.format(utcNoon(sample(day))).asInstanceOf[String]

    override def weekdayLong(day: DayOfWeek): String =
      longWeekday.format(utcNoon(sample(day))).asInstanceOf[String]

    override def date(date: LocalDate): String =
      longDate.format(utcNoon(date)).asInstanceOf[String]
  }
}
