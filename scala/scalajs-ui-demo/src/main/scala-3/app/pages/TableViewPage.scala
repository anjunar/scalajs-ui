package app.pages

import scala.concurrent.Future

import app.components.Showcase.*
import ui.control.table.TableColumn.*
import ui.control.table.TableView.*
import ui.control.table.{
  ColumnResizePolicy,
  CustomColumnResizePolicy,
  TableCell,
  TableColumn,
  TableDirection,
  TableSelectionMode,
  TableSort,
  TableView
}
import ui.core.component.AbstractComponent
import ui.core.remote.{RemoteListProperty, RemoteLoader, RemotePage, RemoteSort}
import ui.core.dsl.AttributeDsl.setAttribute
import ui.core.dsl.ClassDsl.{classIf, classes}
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.layout.Condition.when
import ui.viewport.Overlay.overlay
// minWidth/maxWidth exist in both DSLs. This page bounds column resizing, not boxes, so the
// style variants are hidden rather than every column having to qualify its own setters.
import ui.core.dsl.StyleDsl.{maxWidth as _, minWidth as _, *}
import ui.core.layout.Div.div
import ui.core.layout.Button.button
import ui.core.layout.HBox.hbox
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.Cursor
import ui.core.state.{ListProperty, Property, ReadOnlyProperty}
import ui.core.i18n.{RuntimeMessage, i18n}
import org.scalajs.dom

import scala.scalajs.js

object TableViewPage {
  final case class Book(title: String, author: String, year: Int)
  final case class BookQuery(
      offset: Int,
      limit: Int,
      sorting: Vector[RemoteSort] = Vector.empty
  )

  /** The editing showcase writes through to the row, so its fields are properties rather than a
    * case class: a standard cell commits into the property it was given by `cellValueFactory`.
    */
  final class Reading(
      val title: Property[String],
      val finished: Property[Boolean],
      val shelf: Property[String],
      val progress: Property[Double],
      val rating: Property[Int]
  )

  private val bookCatalog = Vector(
    Book("Der Hobbit", "J. R. R. Tolkien", 1937),
    Book("1984", "George Orwell", 1949),
    Book("Siddhartha", "Hermann Hesse", 1922),
    Book("Der Prozess", "Franz Kafka", 1925),
    Book("Der Zauberberg", "Thomas Mann", 1924),
    Book("Tschick", "Wolfgang Herrndorf", 2010),
    Book("Frankenstein", "Mary Shelley", 1818),
    Book("Stolz und Vorurteil", "Jane Austen", 1813)
  )

  private def generatedBooks(count: Int): Vector[Book] =
    Vector.tabulate(math.max(0, count)) { index =>
      val template = bookCatalog(index % bookCatalog.length)
      template.copy(title = s"${template.title} #${index + 1}")
    }

  def createRemoteBooks(
      rowCount: Int = 1000,
      pageSize: Int = 50,
      offset: Int = 0
  ): RemoteListProperty[Book, BookQuery] = {
    val allBooks           = generatedBooks(rowCount)
    val normalizedPageSize = math.max(1, pageSize)
    val initialQuery       = BookQuery(offset = math.max(0, offset), limit = normalizedPageSize)

    val remote = RemoteListProperty[Book, BookQuery](
      loader = RemoteLoader { query =>
        val sorted     = sortBooks(allBooks, query.sorting)
        val page       = sorted.slice(query.offset, query.offset + query.limit)
        val nextOffset = query.offset + page.length

        Future.successful(
          RemotePage[Book, BookQuery](
            items = page,
            offset = Some(query.offset),
            nextQuery = Option.when(nextOffset < sorted.length)(
              query.copy(offset = nextOffset, limit = normalizedPageSize)
            ),
            totalCount = Some(sorted.length),
            hasMore = Some(nextOffset < sorted.length)
          )
        )
      },
      initialQuery = initialQuery,
      underlying =
        js.Array(allBooks.slice(initialQuery.offset, initialQuery.offset + normalizedPageSize)*),
      sortUpdater = Some((query, sorting) =>
        query.copy(offset = 0, limit = normalizedPageSize, sorting = sorting.toVector)
      ),
      rangeQueryUpdater =
        Some((query, offset, limit) => query.copy(offset = offset, limit = math.max(1, limit)))
    )

    remote.totalCountProperty.set(Some(allBooks.length))
    remote.hasMoreProperty.set(allBooks.length > normalizedPageSize)
    remote.nextQueryProperty.set(
      Option.when(allBooks.length > normalizedPageSize)(
        initialQuery.copy(offset = normalizedPageSize)
      )
    )
    remote
  }

  private def sortBooks(
      books: Vector[Book],
      sorting: Vector[RemoteSort]
  ): Vector[Book] =
    // The simulated remote loader evaluates every term, in priority order.
    books.sortWith { (left, right) =>
      sorting.iterator
        .map { sort =>
          val result = sort.field match {
            case "title"  => left.title.compareToIgnoreCase(right.title)
            case "author" => left.author.compareToIgnoreCase(right.author)
            case "year"   => left.year.compare(right.year)
            case _        => 0
          }
          if (sort.ascending) result else -result
        }
        .find(_ != 0)
        .exists(_ < 0)
    }

  /** One readout of the table's live state. */
  private def statTile(
      label: RuntimeMessage,
      value: ReadOnlyProperty[String],
      wide: Boolean = false
  )(using AbstractComponent, Cursor): Unit =
    div {
      classes =
        if (wide) Seq("table-demo__stat", "table-demo__stat--wide") else Seq("table-demo__stat")
      div { classes = Seq("table-demo__stat-label"); text(label) {} }
      div { classes = Seq("table-demo__stat-value"); text(value) {} }
    }

  /** One labelled block of the control bar: its buttons plus the hints that explain them. */
  private def controlGroup(label: RuntimeMessage, hints: RuntimeMessage*)(
      actions: AbstractComponent ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("table-demo__group")
      div { classes = Seq("table-demo__group-label"); text(label) {} }
      div { classes = Seq("table-demo__group-actions"); actions }
      if (hints.nonEmpty) {
        div {
          classes = Seq("table-demo__hints")
          hints.foreach { hint =>
            div { classes = Seq("table-demo__hint"); text(hint) {} }
          }
        }
      }
    }

  private val readingSeed: Vector[(String, Boolean, String, Double, Int)] = Vector(
    ("Der Hobbit", true, "Archive", 1.0, 5),
    ("1984", false, "Reading", 0.42, 4),
    ("Siddhartha", false, "Backlog", 0.0, 3),
    ("Der Prozess", false, "Reading", 0.18, 4)
  )

  private def readingList(): ListProperty[Reading] =
    ListProperty(
      js.Array(
        readingSeed.map { (title, finished, shelf, progress, rating) =>
          new Reading(
            Property(title),
            Property(finished),
            Property(shelf),
            Property(progress),
            Property(rating)
          )
        }*
      )
    )

  /** Restores the seed values in place, so the edited rows keep their identity. */
  private def resetReadings(readings: ListProperty[Reading]): Unit =
    readings.get.toVector.zip(readingSeed).foreach {
      case (reading, (title, finished, shelf, progress, rating)) =>
        reading.title.set(title)
        reading.finished.set(finished)
        reading.shelf.set(shelf)
        reading.progress.set(progress)
        reading.rating.set(rating)
    }

  def render(books: RemoteListProperty[Book, BookQuery])(using AbstractComponent, Cursor): Unit = {
    val status       = Property("Double-click a row to inspect it.")
    val lastEdit     = Property("—")
    val loadedStatus = books.totalCountProperty.flatMap { totalCount =>
      books.loadedLengthProperty.map(loaded =>
        s"$loaded / ${totalCount.getOrElse(loaded)}"
      )
    }

    showcasePage(i18n"TableView", i18n"Reactive rows with a stable SSR and hydration structure.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Data view",
          i18n"A table should keep changing data calm.",
          i18n"A generated in-memory data source exposes 1,000 rows through RemoteListProperty. The table requests only the visible ranges."
        )

        componentShowcase(
          i18n"Remote in-memory book table",
          i18n"Grouped columns, row and cell selection, resizing, reordering and direction over a remote source."
        ) {
          vbox {
            classes = Seq("table-demo")

            var table: TableView[Book]                  = null
            var authorColumn: TableColumn[Book, String] = null
            var yearColumn: TableColumn[Book, Int]      = null
            var titleColumn: TableColumn[Book, String]  = null

            // C09's remaining "Kontextmenü" piece is deliberately not a second menu API: headerCell
            // is real composition, so a right-click menu reaches the same public Viewport.overlay
            // every other overlay uses (ComboBox's dropdown, C08's column-visibility menu), gated
            // by an ordinary Property the way `when(...)` gates any other conditional content.
            val titleMenuOpen = Property(false)

            // C05: an escape hatch alongside the seven built-in strategies. Applies a resize
            // delta to its own target first, like any built-in policy would, then snaps every
            // visible leaf to a 20px grid -- a real-world case (grid-snapping) none of the seven
            // built-in strategies can express.
            val snapToGridResizePolicy: CustomColumnResizePolicy = request => {
              val next = request.target match {
                case Some((indices, delta)) =>
                  request.widths.zipWithIndex.map((width, index) =>
                    if (indices.contains(index)) width + delta else width
                  )
                case None => request.widths
              }
              next.map(width => math.round(width / 20.0).toDouble * 20.0)
            }

            div {
              classes = Seq("showcase-note")
              div {
                classes = Seq("showcase-note__title")
                text(i18n"50 initial rows · 1,000 total") {}
              }
              div {
                classes = Seq("showcase-note__body")
                text(
                  i18n"Scroll through remote ranges or sort any column; the table keeps one stable virtual surface."
                ) {}
              }
            }

            div {
              classes = Seq("table-page__table")
              style {
                height = "420px"
                minHeight = "0"
              }

              table = tableView[Book](books) {
                style { height = "100%" }
                rowKey = _.title
                rowHeight = 44.0
                crawlable = true
                crawlId = "table"
                tableMenuButtonVisible = true
                columnMenuText = "Columns"
                // The page demonstrates range and rectangle selection, so it starts in the mode
                // where Ctrl/Cmd- and Shift-click actually do something.
                selectionMode = TableSelectionMode.Multiple

                columnGroup[Book]("Book") {
                  titleColumn = column[Book, String]("Title") {
                    prefWidth = 280.0
                    minWidth = 140.0
                    maxWidth = 900.0
                    sortable = true
                    sortKey = "title"
                    // C09: replaces the default header text with an icon + label, and the
                    // default CSS-only sort arrow with a small pill showing direction/priority.
                    // The label also opens a right-click context menu (C09's "Kontextmenü"),
                    // composed with the public headerCell + overlay APIs, not a dedicated one.
                    headerCell {
                      hbox {
                        style { gap = "6px"; alignItems = "center" }
                        text("📖") {}
                        text("Title") {}
                        on("contextmenu") { event =>
                          event.preventDefault()
                          event.stopPropagation()
                          titleMenuOpen.set(true)
                        }
                      }
                      when(titleMenuOpen) {
                        overlay(200.0) {
                          div {
                            classes = Seq("table-demo__header-menu")
                            setAttribute("role", "menu")
                            on("keydown") { event =>
                              event.raw match {
                                case key: dom.KeyboardEvent if key.key == "Escape" =>
                                  titleMenuOpen.set(false)
                                case _ => ()
                              }
                            }
                            button(i18n"Auto-fit column") {
                              setAttribute("role", "menuitem")
                              onClick { _ =>
                                table.autoFitColumn(titleColumn)
                                titleMenuOpen.set(false)
                              }
                            }
                            button(i18n"Hide column") {
                              setAttribute("role", "menuitem")
                              onClick { _ =>
                                titleColumn.visible = false
                                titleMenuOpen.set(false)
                              }
                            }
                          }
                        }
                      }
                    }
                    sortIndicator { state =>
                      div {
                        classes = Seq("table-demo__sort-pill")
                        text(
                          state.map(s =>
                            if (!s.sorted) ""
                            else s"${if (s.ascending) "▲" else "▼"} ${s.priority}"
                          )
                        ) {}
                      }
                    }
                    cell { book =>
                      text(book.title) {}
                    }
                  }

                  authorColumn = column[Book, String]("Author") {
                    prefWidth = 240.0
                    minWidth = 100.0
                    maxWidth = 600.0
                    sortable = true
                    sortKey = "author"
                    cell { book =>
                      text(book.author) {}
                    }
                  }
                }

                columnGroup[Book]("Details") {
                  yearColumn = column[Book, Int]("Year") {
                    prefWidth = 100.0
                    minWidth = 70.0
                    maxWidth = 300.0
                    sortable = true
                    sortKey = "year"
                    // C10: reaches the separately created header/cells, in addition to their own
                    // ui-table-header-cell/ui-table-cell classes.
                    headerClasses = Seq("table-demo__numeric-header")
                    cellClasses = Seq("table-demo__numeric-cell")
                    // D05: TableCell.enclosingCell projects this cell's own focused/selected state
                    // into the lightweight `cell { book => ... }` body, the same state a custom row
                    // already gets for itself via `row.focused`/`row.selected`.
                    cell { book =>
                      val self = TableCell.enclosingCell
                      div {
                        classIf("table-demo__year-cell--focused", self.focusedProperty)
                        classIf("table-demo__year-cell--selected", self.selectedProperty)
                        text(book.year.toString) {}
                      }
                    }
                  }
                }

                header {
                  div {
                    style {
                      padding = "12px 16px"
                      borderBottom = "1px solid var(--aj-line)"
                      color = "var(--aj-ink-soft)"
                    }
                    text(
                      i18n"This content header scrolls with the rows while the column header stays fixed."
                    ) {}
                  }
                }

                placeholder {
                  div {
                    classes = Seq("ui-table-default-placeholder")
                    text(i18n"Loading generated books...") {}
                  }
                }

                onRowDoubleClick((book: Book) => status.set(s"${book.title} — ${book.author}"))
              }
            }

            div {
              classes = Seq("table-demo__stats")
              statTile(i18n"Rows loaded", loadedStatus)
              statTile(
                i18n"Selection mode",
                table.selectionModelProperty
                  .flatMap(_.selectionModeProperty)
                  .map(mode => if (mode == TableSelectionMode.Multiple) "multiple" else "single")
              )
              statTile(
                i18n"Selection target",
                table.selectionModelProperty
                  .flatMap(_.cellSelectionEnabledProperty)
                  .map(enabled => if (enabled) "cells" else "rows")
              )
              statTile(i18n"Selected rows", table.selectedIndicesProperty.map(_.size.toString))
              statTile(i18n"Selected cells", table.selectedCellsProperty.map(_.size.toString))
              statTile(
                i18n"Focused row",
                table.focusedIndexProperty.map(index =>
                  if (index < 0) "—" else (index + 1).toString
                )
              )
              statTile(i18n"Last double-click", status, wide = true)
            }

            div {
              classes = Seq("table-demo__controls")

              controlGroup(
                i18n"Sorting",
                i18n"Shift-click headers to sort by multiple columns. Enter or Space sorts a focused header; Shift keeps other sort columns."
              ) {
                button(i18n"Author ascending, year descending") {
                  onClick(_ =>
                    table.setSortOrder(Seq(TableSort(authorColumn), TableSort(yearColumn, false)))
                  )
                }
                button(i18n"Reload current sorting") {
                  onClick(_ => table.sort())
                }
                button(i18n"Clear sorting") {
                  onClick(_ => table.clearSort())
                }
              }

              controlGroup(
                i18n"Selection",
                i18n"Ctrl/Cmd-click toggles rows; Shift-click selects a range.",
                i18n"In cell mode, Shift-click and Shift+Arrow select an inclusive rectangle.",
                i18n"Disabled rows (V05) stay visible and keyboard-reachable, but cannot be selected or edited."
              ) {
                button(i18n"Toggle single / multiple selection") {
                  onClick { _ =>
                    val model = table.selectionModel
                    model.selectionMode =
                      if (model.selectionMode == TableSelectionMode.Multiple)
                        TableSelectionMode.Single
                      else TableSelectionMode.Multiple
                  }
                }
                button(i18n"Toggle row / cell selection") {
                  onClick { _ =>
                    val model = table.selectionModel
                    model.cellSelectionEnabled = !model.cellSelectionEnabled
                  }
                }
                button(i18n"Clear book selection") {
                  onClick(_ => table.clearSelection())
                }
                button(i18n"Toggle disabling books before 2000") {
                  onClick { _ =>
                    table.rowDisabledProperty.set(
                      if (table.rowDisabledProperty.get.isDefined) None
                      else Some(_.year < 2000)
                    )
                  }
                }
              }

              controlGroup(
                i18n"Columns",
                i18n"Drag a column edge to resize. Focus its grip and use arrow keys for keyboard resizing.",
                i18n"Drag a column header to move it. Or focus the header and press Alt+Shift+Left/Right.",
                i18n"The column menu button in the header corner hides and shows individual columns.",
                i18n"Dragging the \"Book\" group's own edge resizes Title and Author together, spilling into Year only once both are at their own limit.",
                i18n"The snap-to-grid policy replaces every built-in strategy at once, including how the \"Book\" group's own edge behaves.",
                i18n"The Year cell highlights itself when it is focused or selected (D05), using the same per-cell state a custom row already gets for the whole row."
              ) {
                button(i18n"Toggle constrained / free column widths") {
                  onClick { _ =>
                    val current = table.columnResizePolicyProperty.get
                    table.columnResizePolicyProperty.set(
                      if (current == ColumnResizePolicy.Unconstrained)
                        ColumnResizePolicy.FlexLastColumn
                      else ColumnResizePolicy.Unconstrained
                    )
                  }
                }
                button(i18n"Toggle snap-to-grid resize policy") {
                  onClick { _ =>
                    table.customResizePolicyProperty.set(
                      if (table.customResizePolicyProperty.get.isDefined) None
                      else Some(snapToGridResizePolicy)
                    )
                  }
                }
                button(i18n"Toggle left-to-right / right-to-left") {
                  onClick { _ =>
                    val current = table.directionProperty.get
                    table.directionProperty.set(
                      if (current == TableDirection.LeftToRight) TableDirection.RightToLeft
                      else TableDirection.LeftToRight
                    )
                  }
                }
              }

              controlGroup(
                i18n"Navigation",
                i18n"For horizontal navigation, use free widths and widen the columns."
              ) {
                button(i18n"Go to row 500") {
                  onClick(_ => table.scrollTo(499))
                }
                button(i18n"Go to first row") {
                  onClick(_ => table.scrollTo(0))
                }
                button(i18n"Show selected row") {
                  onClick(_ => table.scrollTo(table.selectedIndexProperty.get))
                }
              }
            }
          }
        }

        componentShowcase(
          i18n"Editable cells",
          i18n"Standard cells edit the property the column was given: text, check box, choice box, converting text and a read-only progress bar."
        ) {
          vbox {
            classes = Seq("table-demo")

            val readings = readingList()
            val shelves  = ListProperty(js.Array("Backlog", "Reading", "Archive"))

            div {
              classes = Seq("table-page__table")
              style {
                // Four seed rows plus the column header and the footer, so nothing is clipped.
                height = "320px"
                minHeight = "0"
              }

              tableView[Reading](readings) {
                style { height = "100%" }
                rowHeight = 44.0
                editable = true

                column[Reading, String]("Title") {
                  prefWidth = 240.0
                  cellValueFactory = _.value.title
                  textFieldCell()
                  onEditCommit(event => lastEdit.set(s"Title → ${event.newValue}"))
                }

                column[Reading, Boolean]("Finished") {
                  prefWidth = 110.0
                  cellValueFactory = _.value.finished
                  checkBoxCell
                  onEditCommit(event => lastEdit.set(s"Finished → ${event.newValue}"))
                }

                column[Reading, String]("Shelf") {
                  prefWidth = 150.0
                  cellValueFactory = _.value.shelf
                  choiceBoxCell(shelves)
                  onEditCommit(event => lastEdit.set(s"Shelf → ${event.newValue}"))
                }

                column[Reading, Int]("Rating") {
                  prefWidth = 110.0
                  cellValueFactory = _.value.rating
                  convertingTextFieldCell(text =>
                    text.toIntOption
                      .filter(value => value >= 1 && value <= 5)
                      .toRight("1 to 5 required")
                  )
                  onEditCommit(event => lastEdit.set(s"Rating → ${event.newValue}"))
                }

                column[Reading, Double]("Progress") {
                  prefWidth = 160.0
                  cellValueFactory = _.value.progress
                  progressBarCell
                }
              }
            }

            div {
              classes = Seq("table-demo__stats")
              statTile(i18n"Last commit", lastEdit, wide = true)
            }

            div {
              classes = Seq("table-demo__controls")
              controlGroup(
                i18n"Editing",
                i18n"Double-click a cell, or press Enter on a focused one, to start editing. Enter commits, Escape cancels.",
                i18n"The converting rating cell rejects anything outside 1 to 5 instead of writing it back."
              ) {
                button(i18n"Reset rows") {
                  onClick { _ =>
                    resetReadings(readings)
                    lastEdit.set("—")
                  }
                }
              }
            }
          }
        }

        insightGrid(
          (
            i18n"Memory",
            i18n"The source stays local",
            i18n"A deterministic catalog generates 1,000 rows without a server or network request."
          ),
          (
            i18n"SSR",
            i18n"Initial structure is deterministic",
            i18n"Configuration runs before dynamic row and column mount points are created."
          ),
          (
            i18n"Remote",
            i18n"Large sources remain lazy",
            i18n"RemoteListProperty exposes range loading, placeholders, and sortable query state."
          )
        )

        apiSection(
          i18n"Table DSL",
          i18n"Columns keep their renderer next to the data they display."
        ) {
          codeBlock(
            "scala",
            """|div {
               |  style {
               |    height = "420px"
               |    minHeight = "0"
               |  }
               |
               |  tableView[Book](books) {
               |    style { height = "100%" }
               |    rowHeight = 44.0
               |    tableMenuButtonVisible = true
               |
               |    columnGroup[Book]("Book") {
               |      column[Book, String]("Title") {
               |        prefWidth = 280.0
               |        sortable = true
               |        sortKey = "title"
               |        cell { book =>
               |          text(book.title) {}
               |        }
               |      }
               |    }
               |
               |    header {
               |      div { text("Scrolling content header") {} }
               |    }
               |
               |    onRowDoubleClick(openBook)
               |  }
               |}""".stripMargin
          )
        }

        apiSection(
          i18n"Editable standard cells",
          i18n"A standard cell edits the property its column resolved for the row."
        ) {
          codeBlock(
            "scala",
            """|tableView[Reading](readings) {
               |  editable = true
               |
               |  column[Reading, String]("Title") {
               |    cellValueFactory = _.value.title
               |    textFieldCell()
               |    onEditCommit(event => log(event.newValue))
               |  }
               |
               |  column[Reading, Boolean]("Finished") {
               |    cellValueFactory = _.value.finished
               |    checkBoxCell
               |  }
               |
               |  column[Reading, String]("Shelf") {
               |    cellValueFactory = _.value.shelf
               |    choiceBoxCell(shelves)
               |  }
               |
               |  column[Reading, Int]("Rating") {
               |    cellValueFactory = _.value.rating
               |    convertingTextFieldCell(text =>
               |      text.toIntOption.toRight("Whole number required")
               |    )
               |  }
               |}""".stripMargin
          )
        }

        apiSection(
          i18n"In-memory RemoteListProperty",
          i18n"The loader slices and sorts one generated Vector."
        ) {
          codeBlock(
            "scala",
            """|val books = RemoteListProperty[Book, BookQuery](
               |  loader = RemoteLoader { query =>
               |    val sorted = sortBooks(generatedBooks, query.sorting)
               |    val page = sorted.slice(query.offset, query.offset + query.limit)
               |
               |    Future.successful(
               |      RemotePage(
               |        items = page,
               |        offset = Some(query.offset),
               |        totalCount = Some(sorted.length)
               |      )
               |    )
               |  },
               |  initialQuery = BookQuery(offset = 0, limit = 50),
               |  rangeQueryUpdater = Some((query, offset, limit) =>
               |    query.copy(offset = offset, limit = limit)
               |  )
               |)""".stripMargin
          )
        }
      }
    }
  }
}
