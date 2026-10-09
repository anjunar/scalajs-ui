package ui.control

import ui.control.datagrid.DataGrid
import ui.control.table.TableView
import ui.control.virtuallist.VirtualListView
import ui.control.virtualized.{
  CollectionDisplayMode,
  FixedRowGeometry,
  ItemGeometry,
  VirtualizedCollection
}
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** TableView adds two follow-ups to inherited counters: bumpRemoteState triggers bumpHeaderState,
  * and refreshItemState triggers refreshSelectedItem.
  *
  * Both were lost in the P3-1 consolidation because the base adopted versions from DataGrid and
  * VirtualListView -- only TableView has a header with sorting indicators and selection. Found by a
  * subsequent comparison of all adopted methods with the originals.
  */
class TableViewFollowUpSpec extends AnyFlatSpec with Matchers {

  import ui.control.table.TableColumn.*
  import ui.control.table.TableView.*

  "TableView" should "keep the selected item in sync when the data changes" in {
    val data = ListProperty(scala.scalajs.js.Array("a", "b", "c"))

    var table: TableView[String] | Null = null
    Runtime.mount(
      new AbstractComponent {
        override val tagName: String               = "main"
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            table = tableView[String](data) {
              column[String, String]("Name") {
                cell { item => text(item) {} }
              }
            }
          }
      },
      new SsrCursor()
    )

    val control = table.asInstanceOf[TableView[String]]
    control.select(1)
    control.selectedItemProperty.get shouldBe "b"

    // The data changes beneath the selection. Without refreshSelectedItem following refreshItemState,
    // selectedItemProperty would remain "b".
    data.update(1, "y")

    control.selectedItemProperty.get shouldBe "y"
    // A full replacement no longer silently selects an unrelated object at the same index.
    data.setAll(Seq("x", "z"))
    control.selectedIndexProperty.get shouldBe -1
    control.selectedItemProperty.get shouldBe null
  }
}
