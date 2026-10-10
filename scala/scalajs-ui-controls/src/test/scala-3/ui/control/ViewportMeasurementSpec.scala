package ui.control

import ui.control.datagrid.DataGrid
import ui.control.table.TableView
import ui.control.virtuallist.VirtualListView
import ui.control.virtualized.{CollectionDisplayMode, FixedRowGeometry, ItemGeometry, VirtualizedCollection}
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.scalajs.js.Array
import ui.control.table.ColumnResizePolicy

/** The measured viewport size must be applied on both axes.
  *
  * Regression for P3-1: the shared base initially adopted only height because its
  * updateViewportSize came from VirtualListView -- the only one of the three controls that is
  * single-column and does not need width. TableView and DataGrid therefore stayed at their initial
  * value of 800: the grid showed one column too few and the table distributed column widths over
  * too narrow a surface.
  *
  * The test targets applyViewportSize rather than updateViewportSize so it needs no DOM.
  */
class ViewportMeasurementSpec extends AnyFlatSpec with Matchers {

  "DataGrid" should "take the measured viewport width, not only the height" in {
    import ui.control.datagrid.DataGrid.*

    var grid: DataGrid[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      grid = dataGrid[String](
        ListProperty(Array((0 until 40).map(index => s"Item $index")*))
      ) {
        itemWidthPx = 200
        itemHeightPx = 100
        gapPx = 0
        cellRenderer = renderer
      }
    }

    val control = grid.asInstanceOf[DataGrid[String]]
    control.applyViewportSize(1600.0, 600.0)

    control.viewportWidthProperty.get shouldBe 1600.0
    control.viewportHeightProperty.get shouldBe 600.0
  }

  "TableView" should "take the measured viewport width, not only the height" in {
    import ui.control.table.TableColumn.*
    import ui.control.table.TableView.*

    var table: TableView[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      table = tableView[String](ListProperty(Array("a", "b", "c"))) {
        column[String, String]("Name") {
          prefWidth = 120.0
          cell { item => text(item) {} }
        }
      }
    }

    val control = table.asInstanceOf[TableView[String]]
    control.applyViewportSize(1600.0, 600.0)

    control.viewportWidthProperty.get shouldBe 1600.0
    control.viewportHeightProperty.get shouldBe 600.0
  }

  it should "spread constrained column widths across the measured viewport" in {
    import ui.control.table.TableColumn.*
    import ui.control.table.TableView.*

    var table: TableView[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      table = tableView[String](ListProperty(Array("a", "b", "c"))) {
        columnResizePolicy = ColumnResizePolicy.FlexLastColumn
        column[String, String]("Name") {
          prefWidth = 120.0
          cell { item => text(item) {} }
        }
      }
    }

    val control = table.asInstanceOf[TableView[String]]
    val before  = control.renderedWidthsProperty.get.sum

    control.applyViewportSize(1600.0, 600.0)

    // The column fills the measured width. Previously distribution stayed at the default 800,
    // leaving a gap on the right.
    control.renderedWidthsProperty.get.sum should be > before
    control.renderedWidthsProperty.get.sum shouldBe 1600.0 +- 1.0
  }

  "VirtualListView" should "take the height and ignore the width" in {
    import ui.control.virtuallist.VirtualListView.*

    var list: VirtualListView[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      list = virtualList[String](
        ListProperty(Array((0 until 20).map(index => s"Item $index")*))
      ) {
        estimateHeightPx = 40
        cellRenderer = renderer
      }
    }

    val control = list.asInstanceOf[VirtualListView[String]]
    control.applyViewportSize(1600.0, 600.0)

    control.viewportHeightProperty.get shouldBe 600.0
  }

  "A viewport measurement" should "notify the follow-ups after applying the size" in {
    // Regression: measureViewport temporarily applied only the size in P3-1. The second step -- the
    // hook through which CrawlableCollection restores scroll position and releases hydration -- was
    // lost during consolidation. In the browser, the list jumped to the top after reload and, with
    // hydrating still true, stopped loading more while scrolling.
    val probe = new MeasurementProbe

    probe.measureViewport(1600.0, 600.0)

    probe.notifications shouldBe 1
    probe.viewportWidthSeen shouldBe 1600.0
    probe.viewportHeightProperty.get shouldBe 600.0
  }

  it should "release hydration so lazy loading can start" in {
    val probe = new MeasurementProbe
    probe.startHydrating()

    probe.hydratingNow shouldBe true
    probe.measureViewport(1600.0, 600.0)
    probe.hydratingNow shouldBe false
  }

  it should "ignore an animation-frame callback after disposal" in {
    val probe    = new MeasurementProbe
    var measured = false

    probe.dispose()
    probe.runScheduledViewportMeasure {
      measured = true
    }

    measured shouldBe false
  }

  "A virtualized collection" should "switch its default paging fallback only after hydration" in {
    val probe  = new MeasurementProbe
    val cursor = new BrowserLifecycleCursor(deferred = true)

    probe.initializeBrowserMode(cursor)
    probe.displayModeNow shouldBe CollectionDisplayMode.Paging

    cursor.completeHydration()
    probe.displayModeNow shouldBe CollectionDisplayMode.Scrolling
  }

  it should "keep an explicitly configured paging mode after hydration" in {
    val probe  = new MeasurementProbe
    val cursor = new BrowserLifecycleCursor(deferred = true)

    probe.initializeBrowserMode(cursor, explicitPaging = true)
    cursor.completeHydration()

    probe.displayModeNow shouldBe CollectionDisplayMode.Paging
  }

  it should "use scrolling immediately for a client-only browser mount" in {
    val probe = new MeasurementProbe

    probe.initializeBrowserMode(new BrowserLifecycleCursor(deferred = false))

    probe.displayModeNow shouldBe CollectionDisplayMode.Scrolling
  }

  it should "retain the server-rendered crawl offset when hydration completes later" in {
    val probe  = new MeasurementProbe(itemCount = 30, crawlOffset = 10)
    val cursor = new BrowserLifecycleCursor(deferred = true)

    probe.initializeBrowserMode(cursor)
    probe.measureViewport(800.0, 400.0) // releases the local hydration flag first
    cursor.completeHydration()

    probe.initialScrollIndexNow shouldBe 10
    probe.displayModeNow shouldBe CollectionDisplayMode.Scrolling
  }

  private def renderer[C]: (C | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit =
    (item, index) => div { text(if (item == null) s"Loading $index" else String.valueOf(item)) {} }

  private def render(body: (AbstractComponent, Cursor) => Unit): Unit = {
    Runtime.mount(
      new AbstractComponent {
        override val tagName: String               = "main"
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            body(this, cursor)
          }
      },
      new SsrCursor()
    )
    ()
  }
}
