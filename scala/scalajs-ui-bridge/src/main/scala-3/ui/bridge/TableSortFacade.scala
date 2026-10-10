package ui.bridge

import ui.control.table.{ColumnResizePolicy, TableDirection, TableSelectionMode, TableSort, TableView}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

@js.native
trait TableSortFacade extends js.Object {
  val columnIndex: js.Any = js.native
  val ascending: js.Any   = js.native
}
