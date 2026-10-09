package ui.control.table

/** Stable table coordinate. The column reference survives reordering; `column` is derived from the
  * current visible leaf order and becomes -1 when this is a row-only position or the leaf is
  * hidden.
  */
final case class TablePosition[S](
    tableView: TableView[S],
    row: Int,
    tableColumn: TableColumn[S, ?] | Null
) {
  def column: Int =
    Option(tableColumn).fold(-1)(tableView.getVisibleLeafIndex)
}
