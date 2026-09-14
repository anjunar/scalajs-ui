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

final case class TableEditStartEvent[S, T](
    tableView: TableView[S],
    tableColumn: TableColumn[S, T],
    position: TablePosition[S],
    rowValue: S,
    oldValue: T
)

final case class TableEditCommitEvent[S, T](
    tableView: TableView[S],
    tableColumn: TableColumn[S, T],
    position: TablePosition[S],
    rowValue: S,
    oldValue: T,
    newValue: T
)

final case class TableEditCancelEvent[S, T](
    tableView: TableView[S],
    tableColumn: TableColumn[S, T],
    position: TablePosition[S],
    rowValue: S,
    oldValue: T,
    draftValue: T,
    reason: TableEditCancelReason
)

/** One table-owned edit session. It coordinates state and write-back, but deliberately does not
  * prescribe an editor component or keyboard policy.
  */
final class TableEditModel[S] private[table] (val tableView: TableView[S]) {

  private final class Session[T](
      var row: Int,
      val rowValue: S,
      val column: TableColumn[S, T],
      val observable: ReadOnlyProperty[T],
      val oldValue: T,
      var draftValue: T
  )

  private var session: Session[?] | Null = null
  private var itemRefreshDepth           = 0
  private val editingCellState           = Property[TablePosition[S] | Null](null)
  private val editingItemState           = Property[S | Null](null)
  private val originalValueState         = Property[Any | Null](null)
  private val editingValueState          = Property[Any | Null](null)
  private val editingState               = Property(false)

  val editingCellProperty: ReadOnlyProperty[TablePosition[S] | Null] = editingCellState
  val editingItemProperty: ReadOnlyProperty[S | Null]                = editingItemState
  val originalValueProperty: ReadOnlyProperty[Any | Null]            = originalValueState
  val editingValueProperty: ReadOnlyProperty[Any | Null]             = editingValueState
  val isEditingProperty: ReadOnlyProperty[Boolean]                   = editingState

  def isEditing: Boolean = session != null

  def edit[T](row: Int, column: TableColumn[S, T]): Boolean = {
    if (!tableView.canStartEdit(row, column)) return false
    session match {
      case current: Session[?] if current.row == row && (current.column eq column) => return true
      case _                                                                       => ()
    }

    cancelInternal(TableEditCancelReason.Replaced)
    if (session != null || !tableView.canStartEdit(row, column)) return false
    val rowValue   = tableView.items.itemAt(row).get
    val observable = column.observableValue(rowValue, row)
    if (observable == null) return false
    val oldValue = observable.get

    val next = new Session(row, rowValue, column, observable, oldValue, oldValue)
    session = next
    publish(next)
    column.onEditStartProperty.get.foreach(
      _(TableEditStartEvent(tableView, column, position(next), rowValue, oldValue))
    )
    true
  }

  def updateEdit(value: Any | Null): Boolean = session match {
    case current: Session[?] if !tableView.isDisposed =>
      updateDraft(current, value)
      true
    case _ => false
  }

  def commitEdit(): Boolean = session match {
    case current: Session[?] if !tableView.isDisposed => commit(current)
    case _                                            => false
  }

  def commitEdit(value: Any | Null): Boolean = session match {
    case current: Session[?] if !tableView.isDisposed =>
      updateDraft(current, value)
      commit(current)
    case _ => false
  }

  def cancelEdit(): Boolean =
    if (tableView.isDisposed) false else cancelInternal(TableEditCancelReason.Explicit)

  private def updateDraft[T](current: Session[T], value: Any | Null): Unit = {
    current.draftValue = value.asInstanceOf[T]
    editingValueState.setAlways(value)
  }

  private def commit[T](current: Session[T]): Boolean = {
    val handler  = current.column.editCommitHandlerProperty.get
    val writable = Option(current.observable).collect { case property: WritableProperty[?] =>
      property.asInstanceOf[WritableProperty[T]]
    }
    if (handler.isEmpty && writable.isEmpty) return false

    val event = TableEditCommitEvent(
      tableView,
      current.column,
      position(current),
      current.rowValue,
      current.oldValue,
      current.draftValue
    )
    clearState()
    handler match {
      case Some(commitHandler) => commitHandler(event)
      case None                => writable.get.set(current.draftValue)
    }
    current.column.onEditCommitProperty.get.foreach(_(event))
    true
  }

  private def cancelInternal(reason: TableEditCancelReason): Boolean = session match {
    case current: Session[?] => cancel(current, reason)
    case null                => false
  }

  private def cancel[T](current: Session[T], reason: TableEditCancelReason): Boolean = {
    val event = TableEditCancelEvent(
      tableView,
      current.column,
      position(current),
      current.rowValue,
      current.oldValue,
      current.draftValue,
      reason
    )
    clearState()
    current.column.onEditCancelProperty.get.foreach(_(event))
    true
  }

  private def position[T](current: Session[T]): TablePosition[S] =
    TablePosition(tableView, current.row, current.column)

  private def publish[T](current: Session[T]): Unit = {
    editingCellState.set(position(current))
    editingItemState.set(current.rowValue)
    originalValueState.setAlways(current.oldValue.asInstanceOf[Any | Null])
    editingValueState.setAlways(current.draftValue.asInstanceOf[Any | Null])
    editingState.set(true)
  }

  private def clearState(): Unit = {
    session = null
    editingCellState.set(null)
    editingItemState.set(null)
    originalValueState.setAlways(null)
    editingValueState.setAlways(null)
    editingState.set(false)
  }

  private[table] def reconcile(change: ListDataSource.Change[S]): Unit = session match {
    case current: Session[?] =>
      change match {
        case ListDataSource.Reset(_)            => cancelInternal(TableEditCancelReason.SourceReset)
        case ListDataSource.Add(_, _)           => ()
        case ListDataSource.Insert(index, _, _) => rebase(current, index, 0, 1)
        case ListDataSource.InsertAll(index, elements, _) =>
          rebase(current, index, 0, elements.length)
        case ListDataSource.RemoveAt(index, _, _)           => rebase(current, index, 1, 0)
        case ListDataSource.RemoveRange(index, elements, _) =>
          rebase(current, index, elements.length, 0)
        case ListDataSource.UpdateAt(index, _, _, _) if index == current.row =>
          cancelInternal(TableEditCancelReason.RowReplaced)
        case ListDataSource.UpdateAt(_, _, _, _)              => ()
        case ListDataSource.Patch(from, removed, inserted, _) =>
          rebase(current, from, removed.length, inserted.length)
        case ListDataSource.Clear(_, _) => cancelInternal(TableEditCancelReason.RowRemoved)
      }
    case null => ()
  }

  private def rebase(
      current: Session[?],
      from: Int,
      removed: Int,
      inserted: Int
  ): Unit = {
    if (current.row >= from && current.row < from + removed)
      cancelInternal(
        if (inserted > 0) TableEditCancelReason.RowReplaced else TableEditCancelReason.RowRemoved
      )
    else if (current.row >= from + removed && (removed != inserted || inserted > 0)) {
      current.row += inserted - removed
      editingCellState.set(position(current))
    }
  }

  private[table] def reconcileColumns(visible: Vector[TableColumn[S, ?]]): Unit = session match {
    case current: Session[?] if !visible.exists(_ eq current.column) =>
      cancelInternal(TableEditCancelReason.ColumnUnavailable)
    case current: Session[?] if !tableView.isColumnEditable(current.column) =>
      cancelInternal(TableEditCancelReason.ColumnDisabled)
    case _ => ()
  }

  private[table] def reconcileRangeLoaded(from: Int, untilExclusive: Int): Unit = session match {
    case current: Session[?] if current.row >= from && current.row < untilExclusive =>
      val sameRow = tableView.items
        .itemAt(current.row)
        .exists(value => value.asInstanceOf[AnyRef] eq current.rowValue.asInstanceOf[AnyRef])
      if (!sameRow) cancelInternal(TableEditCancelReason.RowReplaced)
    case _ => ()
  }

  private[table] def tableEditableChanged(): Unit =
    if (!tableView.editableProperty.get) cancelInternal(TableEditCancelReason.TableDisabled)

  private[table] def cellEditableChanged(cell: TableCell[S, ?]): Unit = session match {
    case current: Session[?]
        if !cell.editableProperty.get && current.row == cell.indexProperty.get &&
          (current.column eq cell.tableColumn) =>
      cancelInternal(TableEditCancelReason.CellUnavailable)
    case _ => ()
  }

  private[table] def cellDisposed(cell: TableCell[S, ?]): Unit = session match {
    case current: Session[?]
        if itemRefreshDepth == 0 && current.row == cell.indexProperty.get &&
          (current.column eq cell.tableColumn) =>
      cancelInternal(
        if (tableView.isDisposed) TableEditCancelReason.Disposed
        else TableEditCancelReason.CellUnavailable
      )
    case _ => ()
  }

  private[table] def beginItemRefresh(): Unit = itemRefreshDepth += 1
  private[table] def endItemRefresh(): Unit   = itemRefreshDepth = math.max(0, itemRefreshDepth - 1)

  private[table] def dispose(): Unit = {
    cancelInternal(TableEditCancelReason.Disposed)
    clearState()
  }
}
