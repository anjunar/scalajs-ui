package ui.control.calendar

import ui.control.KeyboardEvents
import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.{addClass, classIf, classes}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Div.div
import ui.core.layout.Span.span
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement, UiEvent}
import ui.core.state.{Property, ReadOnlyProperty}

import java.time.{DayOfWeek, LocalDate, YearMonth}

/** A month to look at and a day to choose, such as the day of an appointment.
  *
  * The month is always six weeks of seven days, so its markup keeps the same shape from month to
  * month, on the server and after hydration. Which days can be chosen and which carry a mark (for
  * example "has free times") is the application's decision; the calendar only shows it. The grid
  * follows the WAI-ARIA date picker pattern: one day is focusable, the arrow keys move a day or a
  * week, Home and End go to the start and end of the week, Page Up and Page Down change the month
  * and, with Shift, the year; Enter and Space choose the focused day.
  */
final class Calendar(
    val selectedDateProperty: Property[Option[LocalDate]] = Property(None),
    configure: Calendar ?=> Unit = Calendar.emptyConfiguration
) extends AbstractComponent {

  override val tagName: String = "div"

  val todayProperty: Property[LocalDate]              = Property(Calendar.localToday())
  val visibleMonthProperty: Property[YearMonth]       = Property(YearMonth.from(todayProperty.get))
  val focusedDateProperty: Property[LocalDate]        = Property(todayProperty.get)
  val firstDayOfWeekProperty: Property[DayOfWeek]     = Property(DayOfWeek.MONDAY)
  val minDateProperty: Property[Option[LocalDate]]    = Property(None)
  val maxDateProperty: Property[Option[LocalDate]]    = Property(None)
  val enabledProperty: Property[LocalDate => Boolean] = Property(Calendar.always)
  val markedProperty: Property[LocalDate => Boolean]  = Property(Calendar.never)
  val localeProperty: Property[String]                = Property("en")
  val formatProperty: Property[CalendarFormat]        = Property(CalendarFormat.intl("en"))
  val previousMonthLabelProperty: Property[String]    = Property("Previous month")
  val nextMonthLabelProperty: Property[String]        = Property("Next month")

  /** Bumped whenever anything a day shows may have changed; every day redraws from the current
    * state.
    */
  private val revision    = Property(0)
  private var monthChosen = false

  def showMonth(month: YearMonth): Unit = {
    monthChosen = true
    if (month != visibleMonthProperty.get) {
      visibleMonthProperty.set(month)
      val focused = focusedDateProperty.get
      if (YearMonth.from(focused) != month)
        focusedDateProperty.set(month.atDay(math.min(focused.getDayOfMonth, month.lengthOfMonth())))
    }
  }

  def showPreviousMonth(): Unit = showMonth(visibleMonthProperty.get.minusMonths(1))

  def showNextMonth(): Unit = showMonth(visibleMonthProperty.get.plusMonths(1))

  /** Whether the day lies within the bounds and the application lets it be chosen. */
  def selectable(date: LocalDate): Boolean =
    minDateProperty.get.forall(!date.isBefore(_)) && maxDateProperty.get.forall(!date.isAfter(_)) &&
      enabledProperty.get(date)

  /** Chooses the day if it can be chosen; a day of a neighbouring month also shows that month. */
  def select(date: LocalDate): Unit =
    if (selectable(date)) {
      focusedDateProperty.set(date)
      showMonth(YearMonth.from(date))
      selectedDateProperty.set(Some(date))
    }

  /** The first day of the six weeks shown: the start of the week that holds the first of the month.
    */
  def firstShownDate: LocalDate = {
    val first = visibleMonthProperty.get.atDay(1)
    val shift = Math.floorMod(first.getDayOfWeek.getValue - firstDayOfWeekProperty.get.getValue, 7)
    first.minusDays(shift.toLong)
  }

  private def shownDate(index: Int): LocalDate = firstShownDate.plusDays(index.toLong)

  private def monthReachable(month: YearMonth): Boolean =
    minDateProperty.get.forall(min => !month.atEndOfMonth().isBefore(min)) &&
      maxDateProperty.get.forall(max => !month.atDay(1).isAfter(max))

  override def compose(cursor: Cursor): Unit = {
    configure(using this)
    if (!monthChosen)
      selectedDateProperty.get.foreach(date => visibleMonthProperty.set(YearMonth.from(date)))
    focusedDateProperty.set(initialFocus())

    Seq(
      todayProperty,
      visibleMonthProperty,
      focusedDateProperty,
      firstDayOfWeekProperty,
      minDateProperty,
      maxDateProperty,
      enabledProperty,
      markedProperty,
      formatProperty,
      selectedDateProperty
    ).foreach(property =>
      addDisposable(property.observeWithoutInitial(_ => revision.setAlways(revision.get + 1)))
    )

    def current[T](read: => T): ReadOnlyProperty[T] = revision.map(_ => read)

    DslLayer.render(this, cursor) {
      addClass("ui-calendar")

      div {
        classes = Seq("ui-calendar__header")
        button("‹") { previous ?=>
          classes = Seq("ui-calendar__nav", "ui-calendar__nav--previous")
          buttonType("button")
          previous.addDisposable(
            current(previousMonthLabelProperty.get).observe(setDslAttribute("aria-label", _))
          )
          previous.addDisposable(
            current(!monthReachable(visibleMonthProperty.get.minusMonths(1))).observe { off =>
              setDslAttribute("aria-disabled", off.toString)
            }
          )
          onClick(_ =>
            if (monthReachable(visibleMonthProperty.get.minusMonths(1))) showPreviousMonth()
          )
        }
        span {
          classes = Seq("ui-calendar__title")
          setDslAttribute("aria-live", "polite")
          text(current(formatProperty.get.month(visibleMonthProperty.get))) {}
        }
        button("›") { next ?=>
          classes = Seq("ui-calendar__nav", "ui-calendar__nav--next")
          buttonType("button")
          next.addDisposable(
            current(nextMonthLabelProperty.get).observe(setDslAttribute("aria-label", _))
          )
          next.addDisposable(
            current(!monthReachable(visibleMonthProperty.get.plusMonths(1))).observe { off =>
              setDslAttribute("aria-disabled", off.toString)
            }
          )
          onClick(_ => if (monthReachable(visibleMonthProperty.get.plusMonths(1))) showNextMonth())
        }
      }

      div { grid ?=>
        classes = Seq("ui-calendar__grid")
        setDslAttribute("role", "grid")
        grid.addDisposable(current(formatProperty.get.month(visibleMonthProperty.get)).observe {
          label =>
            setDslAttribute("aria-label", label)
        })
        on("keydown")(handleKeyDown)

        div {
          classes = Seq("ui-calendar__weekdays")
          setDslAttribute("role", "row")
          (0 until 7).foreach { column =>
            def weekday: DayOfWeek = firstDayOfWeekProperty.get.plus(column.toLong)
            span { header ?=>
              classes = Seq("ui-calendar__weekday")
              setDslAttribute("role", "columnheader")
              header.addDisposable(current(formatProperty.get.weekdayLong(weekday)).observe {
                name =>
                  setDslAttribute("aria-label", name)
                  setDslAttribute("title", name)
              })
              text(current(formatProperty.get.weekdayShort(weekday))) {}
            }
          }
        }

        (0 until 6).foreach { week =>
          div {
            classes = Seq("ui-calendar__week")
            setDslAttribute("role", "row")
            (0 until 7).foreach { column =>
              val index           = week * 7 + column
              def date: LocalDate = shownDate(index)

              button(current(date.getDayOfMonth.toString)) { day ?=>
                classes = Seq("ui-calendar__day")
                buttonType("button")
                setDslAttribute("role", "gridcell")
                classIf(
                  "ui-calendar__day--outside",
                  current(YearMonth.from(date) != visibleMonthProperty.get)
                )
                classIf("ui-calendar__day--today", current(date == todayProperty.get))
                classIf(
                  "ui-calendar__day--selected",
                  current(selectedDateProperty.get.contains(date))
                )
                classIf("ui-calendar__day--disabled", current(!selectable(date)))
                classIf("ui-calendar__day--marked", current(markedProperty.get(date)))
                day.addDisposable(revision.observe { _ =>
                  val shown = date
                  setDslAttribute("data-date", shown.toString)
                  setDslAttribute("aria-label", formatProperty.get.date(shown))
                  setDslAttribute(
                    "aria-selected",
                    selectedDateProperty.get.contains(shown).toString
                  )
                  setDslAttribute("aria-disabled", (!selectable(shown)).toString)
                  setDslAttribute(
                    "aria-current",
                    if (shown == todayProperty.get) "date" else "false"
                  )
                  setDslAttribute("tabindex", if (shown == focusedDateProperty.get) "0" else "-1")
                })
                onClick(_ => {
                  focusedDateProperty.set(date)
                  select(date)
                })
              }
            }
          }
        }
      }
    }
  }

  /** The chosen day if it is shown, else today if it is shown, else the first of the month. */
  private def initialFocus(): LocalDate = {
    val month = visibleMonthProperty.get
    selectedDateProperty.get
      .filter(YearMonth.from(_) == month)
      .orElse(Some(todayProperty.get).filter(YearMonth.from(_) == month))
      .getOrElse(month.atDay(1))
  }

  private def handleKeyDown(event: UiEvent): Unit =
    if (KeyboardEvents.isPlain(event)) {
      val focused = focusedDateProperty.get
      val year    = KeyboardEvents.shift(event)
      val target  = KeyboardEvents.key(event).collect {
        case "ArrowLeft"        => focused.minusDays(1)
        case "ArrowRight"       => focused.plusDays(1)
        case "ArrowUp"          => focused.minusWeeks(1)
        case "ArrowDown"        => focused.plusWeeks(1)
        case "Home"             => focused.minusDays(weekPosition(focused).toLong)
        case "End"              => focused.plusDays((6 - weekPosition(focused)).toLong)
        case "PageUp" if year   => focused.minusYears(1)
        case "PageUp"           => focused.minusMonths(1)
        case "PageDown" if year => focused.plusYears(1)
        case "PageDown"         => focused.plusMonths(1)
        case "Enter" | " "      => focused
      }
      target.foreach { date =>
        event.preventDefault()
        KeyboardEvents.key(event) match {
          case Some("Enter" | " ") =>
            select(date)
          case _ =>
            focusedDateProperty.set(date)
            showMonth(YearMonth.from(date))
            focusDay(date)
        }
      }
    }

  private def weekPosition(date: LocalDate): Int =
    Math.floorMod(date.getDayOfWeek.getValue - firstDayOfWeekProperty.get.getValue, 7)

  /** Moves the browser's focus to the day, which the roving tabindex has just made focusable. */
  private def focusDay(date: LocalDate): Unit =
    host match {
      case dom: DomHostElement =>
        Option(dom.node.querySelector(s"[data-date='$date']")).foreach(
          _.asInstanceOf[scala.scalajs.js.Dynamic].focus()
        )
      case _ => ()
    }
}

object Calendar {

  /** Today in the local time of the browser or server. java.time's own clock would need a time-zone
    * database.
    */
  def localToday(): LocalDate = {
    val now = new scala.scalajs.js.Date()
    LocalDate.of(now.getFullYear().toInt, now.getMonth().toInt + 1, now.getDate().toInt)
  }

  private val always: LocalDate => Boolean = _ => true
  private val never: LocalDate => Boolean  = _ => false

  private val emptyConfiguration: Calendar ?=> Unit =
    (_: Calendar) ?=> ()

  def calendar(
      selected: Property[Option[LocalDate]] = Property(None)
  )(body: Calendar ?=> Unit = emptyConfiguration)(using AbstractComponent, Cursor): Calendar =
    DslLayer.child(new Calendar(selected, body)) {}

  /** The language of the names, e.g. "de"; it replaces the format with the platform's names in that
    * language.
    */
  def locale(using calendar: Calendar): String = calendar.localeProperty.get

  def locale_=(value: String)(using calendar: Calendar): Unit = {
    calendar.localeProperty.set(value)
    calendar.formatProperty.set(CalendarFormat.intl(value))
  }

  def format(using calendar: Calendar): CalendarFormat = calendar.formatProperty.get

  def format_=(value: CalendarFormat)(using calendar: Calendar): Unit =
    calendar.formatProperty.set(value)

  def today(using calendar: Calendar): LocalDate = calendar.todayProperty.get

  def today_=(value: LocalDate)(using calendar: Calendar): Unit =
    calendar.todayProperty.set(value)

  def visibleMonth(using calendar: Calendar): YearMonth = calendar.visibleMonthProperty.get

  def visibleMonth_=(value: YearMonth)(using calendar: Calendar): Unit =
    calendar.showMonth(value)

  def firstDayOfWeek(using calendar: Calendar): DayOfWeek = calendar.firstDayOfWeekProperty.get

  def firstDayOfWeek_=(value: DayOfWeek)(using calendar: Calendar): Unit =
    calendar.firstDayOfWeekProperty.set(value)

  def minDate(using calendar: Calendar): Option[LocalDate] = calendar.minDateProperty.get

  def minDate_=(value: LocalDate)(using calendar: Calendar): Unit =
    calendar.minDateProperty.set(Some(value))

  def maxDate(using calendar: Calendar): Option[LocalDate] = calendar.maxDateProperty.get

  def maxDate_=(value: LocalDate)(using calendar: Calendar): Unit =
    calendar.maxDateProperty.set(Some(value))

  def enabled(using calendar: Calendar): LocalDate => Boolean = calendar.enabledProperty.get

  /** Which days can be chosen besides the bounds, e.g. the days with free times. */
  def enabled_=(value: LocalDate => Boolean)(using calendar: Calendar): Unit =
    calendar.enabledProperty.set(value)

  def enabled_=(value: ReadOnlyProperty[LocalDate => Boolean])(using calendar: Calendar): Unit =
    calendar.addDisposable(value.observe(calendar.enabledProperty.set))

  def marked(using calendar: Calendar): LocalDate => Boolean = calendar.markedProperty.get

  /** Which days carry a mark, e.g. "has free times". */
  def marked_=(value: LocalDate => Boolean)(using calendar: Calendar): Unit =
    calendar.markedProperty.set(value)

  def marked_=(value: ReadOnlyProperty[LocalDate => Boolean])(using calendar: Calendar): Unit =
    calendar.addDisposable(value.observe(calendar.markedProperty.set))

  def previousMonthLabel(using calendar: Calendar): String = calendar.previousMonthLabelProperty.get

  def previousMonthLabel_=(value: String)(using calendar: Calendar): Unit =
    calendar.previousMonthLabelProperty.set(value)

  def nextMonthLabel(using calendar: Calendar): String = calendar.nextMonthLabelProperty.get

  def nextMonthLabel_=(value: String)(using calendar: Calendar): Unit =
    calendar.nextMonthLabelProperty.set(value)
}
