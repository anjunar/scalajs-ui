package ui.control.table

import ui.core.state.{ListDataSource, Property, ReadOnlyProperty, WritableProperty}

final case class TableEditCommitEvent[S, T](
    tableView: TableView[S],
    tableColumn: TableColumn[S, T],
    position: TablePosition[S],
    rowValue: S,
    oldValue: T,
    newValue: T
)

