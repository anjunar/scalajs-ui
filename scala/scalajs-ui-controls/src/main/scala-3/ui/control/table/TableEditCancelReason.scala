package ui.control.table

import ui.core.state.{ListDataSource, Property, ReadOnlyProperty, WritableProperty}

enum TableEditCancelReason {
  case Explicit
  case Replaced
  case TableDisabled
  case ColumnDisabled
  case RowRemoved
  case RowReplaced
  case SourceReset
  case ColumnUnavailable
  case CellUnavailable
  case Disposed
}

