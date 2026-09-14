package ui.control.table

import ui.core.state.{ListDataSource, Property, ReadOnlyProperty}

/** Logical row/cell focus, independent of selection, scrolling and native DOM focus. A known remote
  * gap has an index but no focused item. Columns use stable leaf references across reordering.
  */
class TableFocusModel[S](final val tableView: TableView[S]) {
  require(tableView != null, "A focus model requires a TableView")
  protected val table: TableView[S] = tableView
  table.registerFocusModel(this)
  private case class State(
      index: Int,
      item: Option[S],
      column: Option[TableColumn[S, ?]]
  )
  private val state                                   = Property(State(-1, None, None))
  val focusedIndexProperty: ReadOnlyProperty[Int]     = state.map(_.index)
  val focusedItemProperty: ReadOnlyProperty[S | Null] = state.map(_.item.orNull)
  val focusedCellProperty: ReadOnlyProperty[TablePosition[S] | Null] = state.map { current =>
    if (current.index < 0) null
    else TablePosition(table, current.index, current.column.orNull)
  }
  def focusedIndex: Int                       = state.get.index
  def focusedItem: S | Null                   = focusedItemProperty.get
  def focusedCell: TablePosition[S] | Null    = focusedCellProperty.get
  def focusedColumn: TableColumn[S, ?] | Null = state.get.column.orNull
  def isFocused(index: Int): Boolean          = index >= 0 && index == focusedIndex
  def isFocused(index: Int, column: TableColumn[S, ?]): Boolean =
    isFocused(index) && state.get.column.contains(column)
  private def count: Int = math.max(0, table.items.totalLength)

  /** Focuses a row without a cell coordinate. */
  def focus(index: Int): Unit = publish(index, None)

  /** Focuses one visible leaf cell. Invalid coordinates clear focus. */
  def focus(index: Int, column: TableColumn[S, ?]): Unit =
    Option(column).filter(table.getVisibleLeafIndex(_) >= 0) match {
      case Some(leaf) => publish(index, Some(leaf))
      case None       => publish(-1, None)
    }

  def focusLeftCell(): Unit  = moveHorizontal(-1)
  def focusRightCell(): Unit = moveHorizontal(1)
  def focusAboveCell(): Unit = moveVertical(-1)
  def focusBelowCell(): Unit = moveVertical(1)

  def focusNext(): Unit =
    if (focusedIndex < count - 1) publish(focusedIndex + 1, state.get.column)
  def focusPrevious(): Unit =
    if (focusedIndex > 0) publish(focusedIndex - 1, state.get.column)

  private def moveHorizontal(delta: Int): Unit = if (!table.isDisposed && focusedIndex >= 0) {
    val leaves  = table.visibleLeafColumns.get
    val current = state.get.column.flatMap(column =>
      leaves.indexOf(column) match {
        case -1    => None
        case index => Some(index)
      }
    )
    val next = current match {
      case Some(index) => math.max(0, math.min(leaves.size - 1, index + delta))
      case None        => if (delta < 0) leaves.size - 1 else 0
    }
    leaves.lift(next).foreach(column => publish(focusedIndex, Some(column)))
  }

  private def moveVertical(delta: Int): Unit = if (!table.isDisposed && focusedIndex >= 0) {
    val next = math.max(0, math.min(count - 1, focusedIndex + delta))
    publish(next, state.get.column)
  }

  private def publish(index: Int, column: Option[TableColumn[S, ?]]): Unit =
    if (!table.isDisposed) {
      val next =
        if (index >= 0 && index < count)
          State(index, table.items.itemAt(index), column.filter(table.getVisibleLeafIndex(_) >= 0))
        else State(-1, None, None)
      val old      = state.get
      val sameItem = (old.item, next.item) match {
        case (Some(a), Some(b)) => a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]
        case (None, None)       => true
        case _                  => false
      }
      if (old.index != next.index || !sameItem || old.column != next.column) state.setAlways(next)
    }

  protected[table] def refresh(): Unit = publish(focusedIndex, state.get.column)

  /** Hidden/removed leaves degrade a cell coordinate to its still-focused row. Reordering keeps the
    * column reference and republishes so derived visible indices update.
    */
  protected[table] def reconcileColumns(): Unit = if (!table.isDisposed && focusedIndex >= 0) {
    val current = state.get
    val column  = current.column.filter(table.getVisibleLeafIndex(_) >= 0)
    if (current.column != column) publish(current.index, None)
    else if (column.nonEmpty) state.setAlways(current)
  }

  protected[table] def reconcile(change: ListDataSource.Change[S]): Unit = {
    if (table.isDisposed) return
    val old                                                 = state.get
    def splice(from: Int, removed: Int, inserted: Int): Int =
      if (old.index < from) old.index
      else if (old.index < from + removed) -1
      else old.index + inserted - removed
    val next = change match {
      case ListDataSource.Insert(index, _, _)               => splice(index, 0, 1)
      case ListDataSource.InsertAll(index, values, _)       => splice(index, 0, values.length)
      case ListDataSource.RemoveAt(index, _, _)             => splice(index, 1, 0)
      case ListDataSource.RemoveRange(index, values, _)     => splice(index, values.length, 0)
      case ListDataSource.Patch(from, removed, inserted, _) =>
        splice(from, removed.length, inserted.length)
      case ListDataSource.Clear(_, _) => -1
      case ListDataSource.Reset(_)    => resetIndex(old, allowReferenceFallback = true)
      case _                          => old.index
    }
    publish(next, old.column)
  }

  protected[table] def reconcileReset(allowReferenceFallback: Boolean): Unit =
    if (!table.isDisposed)
      publish(resetIndex(state.get, allowReferenceFallback), state.get.column)

  private def resetIndex(old: State, allowReferenceFallback: Boolean): Int =
    table.rowKeyProperty.get match {
      case Some(rowKey) =>
        old.item
          .flatMap(TableRowIdentity.keyOf(_, rowKey))
          .flatMap(key => TableRowIdentity.locate(table.items, rowKey, Set(key)).get(key))
          .getOrElse(-1)
      case None if allowReferenceFallback =>
        // Preserve only a unique reference; equal records and duplicate occurrences are ambiguous.
        old.item.fold(-1) { item =>
          var found = -1
          var index = 0
          while (index < count && found != -2) {
            if (
              table.items
                .itemAt(index)
                .exists(value => value.asInstanceOf[AnyRef] eq item.asInstanceOf[AnyRef])
            )
              found = if (found == -1) index else -2
            index += 1
          }
          found
        }
      case None => -1
    }
}
