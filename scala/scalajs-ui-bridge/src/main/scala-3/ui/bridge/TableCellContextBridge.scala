package ui.bridge

import ui.control.table.TableCell
import scala.scalajs.js

/** Read-only cell state (D05) -- the same `index`/`empty`/`selected`/`focused`/`editing`
  * properties a `TableCell` subclass's own `renderContent` already reaches via `this`, projected
  * for the lighter-weight `cell`/`valueCell` bridge renderers, which only received the bare item
  * before this. Rendering and component ownership remain in Scala, the same split
  * [[TableRowContextBridge]] uses for a custom row.
  */
final class TableCellContextBridge(cell: TableCell[js.Any, js.Any]) extends js.Object {
  val index    = new ReadOnlyPropertyHandle(cell.indexProperty)
  val empty    = new ReadOnlyPropertyHandle(cell.emptyProperty)
  val selected = new ReadOnlyPropertyHandle(cell.selectedProperty)
  val focused  = new ReadOnlyPropertyHandle(cell.focusedProperty)
  val editing  = new ReadOnlyPropertyHandle(cell.editingProperty)
}
