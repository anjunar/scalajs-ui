package ui.control.calendar

import ui.control.calendar.Calendar.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{DayOfWeek, LocalDate, YearMonth}
import scala.scalajs.js

class CalendarSpec extends AnyFlatSpec with Matchers {

  private val today = LocalDate.of(2026, 10, 27)

  private final class Root(body: AbstractComponent ?=> Cursor ?=> Unit) extends AbstractComponent {
    override val tagName: String               = "main"
    override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor)(body)
  }

  private def calendarOf(
      selected: Property[Option[LocalDate]] = Property(None)
  )(configure: Calendar ?=> Unit): (Calendar, SsrCursor) = {
    var control: Calendar = null
    val cursor            = new SsrCursor()
    Runtime.mount(
      new Root({
        control = calendar(selected) {
          Calendar.today = today
          configure
        }
      }),
      cursor
    )
    (control, cursor)
  }

  /** The `data-date` values of the days in their order. */
  private def shownDates(html: String): Seq[String] =
    "data-date=\"([0-9-]+)\"".r.findAllMatchIn(html).map(_.group(1)).toSeq

  private def dayTag(html: String, date: String): String =
    ("<button[^>]*data-date=\"" + date + "\"[^>]*>").r
      .findFirstIn(html)
      .getOrElse(fail(s"no day $date"))

  private def tagWithClass(html: String, className: String): String =
    ("<button[^>]*" + className + "[^>]*>").r.findFirstIn(html).getOrElse(fail(s"no $className"))

  "Calendar" should "render six weeks starting on the configured weekday, with names in its language" in {
    val (_, cursor) = calendarOf() {
      locale = "de"
    }
    val html = cursor.collectHtml()

    html should include("class=\"ui-calendar\"")
    html should include("role=\"grid\"")
    html should include("Oktober 2026")
    html should include(">Mo<")
    html should include("title=\"Montag\"")
    val dates = shownDates(html)
    dates.size shouldBe 42
    // October 2026 starts on a Thursday: the grid starts on Monday, 28 September.
    dates.head shouldBe "2026-09-28"
    dates.last shouldBe "2026-11-08"
    dayTag(html, "2026-10-27") should include("aria-current=\"date\"")
    dayTag(html, "2026-10-27") should include("ui-calendar__day--today")
    dayTag(html, "2026-09-30") should include("ui-calendar__day--outside")
    dayTag(html, "2026-10-27") should include("aria-label=\"Dienstag, 27. Oktober 2026\"")
  }

  it should "start the week on Sunday when asked" in {
    val (_, cursor) = calendarOf() {
      firstDayOfWeek = DayOfWeek.SUNDAY
    }
    shownDates(cursor.collectHtml()).head shouldBe "2026-09-27"
  }

  it should "open on the month of the chosen day and make only that day focusable" in {
    val selected    = Property(Option(LocalDate.of(2027, 2, 14)))
    val (_, cursor) = calendarOf(selected) {}
    val html        = cursor.collectHtml()

    shownDates(html) should contain("2027-02-14")
    dayTag(html, "2027-02-14") should include("aria-selected=\"true\"")
    dayTag(html, "2027-02-14") should include("tabindex=\"0\"")
    "tabindex=\"0\"".r.findAllMatchIn(html).size shouldBe 1
  }

  it should "choose only days within the bounds that the application enables" in {
    val selected          = Property(Option.empty[LocalDate])
    val (control, cursor) = calendarOf(selected) {
      minDate = today
      maxDate = today.plusDays(10)
      enabled = (date: LocalDate) => date.getDayOfWeek != DayOfWeek.SUNDAY
      marked = (date: LocalDate) => date == today.plusDays(1)
    }

    control.select(today.minusDays(1))
    selected.get shouldBe None
    control.select(LocalDate.of(2026, 11, 1)) // a Sunday
    selected.get shouldBe None
    control.select(today.plusDays(11))
    selected.get shouldBe None

    control.select(today.plusDays(1))
    selected.get shouldBe Some(today.plusDays(1))

    val html = cursor.collectHtml()
    dayTag(html, "2026-10-26") should include("aria-disabled=\"true\"")
    dayTag(html, "2026-11-01") should include("ui-calendar__day--disabled")
    dayTag(html, "2026-10-28") should include("ui-calendar__day--marked")
    dayTag(html, "2026-10-28") should include("ui-calendar__day--selected")
    dayTag(html, "2026-10-28") should include("aria-selected=\"true\"")
  }

  it should "redraw the same six weeks for another month, and not page past its bounds" in {
    val (control, cursor) = calendarOf() {
      minDate = LocalDate.of(2026, 10, 1)
      maxDate = LocalDate.of(2026, 11, 30)
    }

    control.showNextMonth()
    control.visibleMonthProperty.get shouldBe YearMonth.of(2026, 11)
    val html = cursor.collectHtml()
    shownDates(html).head shouldBe "2026-10-26"
    html should include("November 2026")
    tagWithClass(html, "ui-calendar__nav--next") should include("aria-disabled=\"true\"")
    tagWithClass(html, "ui-calendar__nav--previous") should include("aria-disabled=\"false\"")
    // The focused day follows the month, keeping its day of the month.
    control.focusedDateProperty.get shouldBe LocalDate.of(2026, 11, 27)
  }

  it should "move through days, weeks, months and years with the keyboard and choose with Enter" in {
    val selected          = Property(Option(today))
    var control: Calendar = null
    val grids  = scala.collection.mutable.ArrayBuffer.empty[ui.control.TabsTestHostElement]
    val cursor = new ui.control.TabsEventCursor(host => grids += host)
    Runtime.mount(
      new Root({
        control = calendar(selected) {
          Calendar.today = today
        }
      }),
      cursor
    )
    val grid = grids.find(_.attribute("role").contains("grid")).get
    def press(key: String, shift: Boolean = false): Unit =
      grid.fire("keydown", js.Dynamic.literal(key = key, shiftKey = shift)) shouldBe true

    press("ArrowRight")
    control.focusedDateProperty.get shouldBe LocalDate.of(2026, 10, 28)
    press("ArrowDown")
    control.focusedDateProperty.get shouldBe LocalDate.of(2026, 11, 4)
    control.visibleMonthProperty.get shouldBe YearMonth.of(2026, 11)
    press("Home")
    control.focusedDateProperty.get shouldBe LocalDate.of(2026, 11, 2)
    press("End")
    control.focusedDateProperty.get shouldBe LocalDate.of(2026, 11, 8)
    press("PageUp")
    control.focusedDateProperty.get shouldBe LocalDate.of(2026, 10, 8)
    press("PageDown", shift = true)
    control.focusedDateProperty.get shouldBe LocalDate.of(2027, 10, 8)
    control.visibleMonthProperty.get shouldBe YearMonth.of(2027, 10)
    selected.get shouldBe Some(today)

    press("Enter")
    selected.get shouldBe Some(LocalDate.of(2027, 10, 8))

    // Shortcuts belong to someone else.
    grid.fire("keydown", js.Dynamic.literal(key = "ArrowLeft", ctrlKey = true)) shouldBe false
    control.focusedDateProperty.get shouldBe LocalDate.of(2027, 10, 8)
  }
}
