package ui.control.table

import ui.core.state.{ListDataSource, Property, ReadOnlyProperty}

enum TableSelectionMode {
  case Single, Multiple
}

/** Runtime-owned row/cell selection. One snapshot keeps mode, lead, results and Shift anchors
  * coherent. Unloaded remote positions are selected coordinates, not fabricated selected items.
  */
class TableSelectionModel[S](final val tableView: TableView[S]) {
  require(tableView != null, "A selection model requires a TableView")
  protected val table: TableView[S] = tableView
  table.registerSelectionModel(this)
  private case class Entry(index: Int, item: Option[S])
  private case class Coordinate(row: Int, column: TableColumn[S, ?])
  private case class CellEntry(coordinate: Coordinate, item: Option[S])
  private case class Snapshot(
      mode: TableSelectionMode,
      cellSelectionEnabled: Boolean,
      entries: Vector[Entry],
      lead: Int,
      anchor: Int,
      anchorItem: Option[S],
      cells: Vector[CellEntry],
      cellLead: Option[Coordinate],
      cellAnchor: Option[Coordinate]
  ) {
    val indices: Vector[Int] = entries.map(_.index)
    val indexSet: Set[Int]   = indices.toSet
    val items: Vector[S]     = entries.flatMap(_.item)
    val leadItem: S | Null   = entries.find(_.index == lead).flatMap(_.item).orNull
  }

  private val state = Property(
    Snapshot(TableSelectionMode.Single, false, Vector.empty, -1, -1, None, Vector.empty, None, None)
  )
  val selectionModeProperty: ReadOnlyProperty[TableSelectionMode] = state.map(_.mode)
  val cellSelectionEnabledProperty: ReadOnlyProperty[Boolean] = state.map(_.cellSelectionEnabled)
  val selectedIndexProperty: ReadOnlyProperty[Int]            = state.map(_.lead)
  val selectedItemProperty: ReadOnlyProperty[S | Null]        = state.map(_.leadItem)
  val selectedIndicesProperty: ReadOnlyProperty[Vector[Int]]  = state.map(_.indices)
  val selectedItemsProperty: ReadOnlyProperty[Vector[S]]      = state.map(_.items)
  val selectedCellsProperty: ReadOnlyProperty[Vector[TablePosition[S]]] = state.map { snapshot =>
    if (snapshot.cellSelectionEnabled)
      snapshot.cells.map(cell => TablePosition(table, cell.coordinate.row, cell.coordinate.column))
    else snapshot.entries.map(entry => TablePosition(table, entry.index, null))
  }

  def selectionMode: TableSelectionMode               = state.get.mode
  def selectionMode_=(mode: TableSelectionMode): Unit = {
    require(mode != null, "Selection mode must not be null")
    val previous = state.get
    if (previous.cellSelectionEnabled)
      publishCells(previous.cells.map(_.coordinate), previous.cellLead, previous.cellAnchor, mode)
    else publishRows(previous.indices, previous.lead, previous.anchor, mode)
  }

  def cellSelectionEnabled: Boolean                  = state.get.cellSelectionEnabled
  def cellSelectionEnabled_=(enabled: Boolean): Unit = {
    if (table.isDisposed || enabled == cellSelectionEnabled) return
    val previous = state.get
    if (enabled) {
      val column = activeColumn
      publishCells(
        column.fold(Vector.empty[Coordinate])(col => previous.indices.map(Coordinate(_, col))),
        column.filter(_ => valid(previous.lead)).map(Coordinate(previous.lead, _)),
        column.filter(_ => valid(previous.anchor)).map(Coordinate(previous.anchor, _)),
        previous.mode,
        enabled = true
      )
    } else
      publishRows(previous.indices, previous.lead, previous.anchor, previous.mode, enabled = false)
  }

  def isSelected(index: Int): Boolean = state.get.indexSet.contains(index)
  def isSelected(index: Int, column: TableColumn[S, ?]): Boolean =
    if (!cellSelectionEnabled) isSelected(index)
    else state.get.cells.exists(_.coordinate == Coordinate(index, column))
  def isEmpty: Boolean                                    = state.get.entries.isEmpty
  private def count: Int                                  = math.max(0, table.items.totalLength)
  private def valid(index: Int): Boolean                  = index >= 0 && index < count
  private def visible(column: TableColumn[S, ?]): Boolean =
    column != null && table.getVisibleLeafIndex(column) >= 0
  private def activeColumn: Option[TableColumn[S, ?]] =
    Option(table.focusModel.focusedColumn)
      .filter(visible)
      .orElse(table.visibleLeafColumns.get.headOption)

  /** Invalid single-index requests retain the existing UI contract: clear selection. */
  def select(index: Int): Unit =
    if (!valid(index)) clearSelection()
    else if (cellSelectionEnabled)
      activeColumn.fold(clearSelection())(select(index, _))
    else publishRows(state.get.indices :+ index, index, index)

  def select(index: Int, column: TableColumn[S, ?]): Unit =
    if (!cellSelectionEnabled) select(index)
    else if (!valid(index) || !visible(column)) clearSelection()
    else {
      val coordinate = Coordinate(index, column)
      publishCells(
        state.get.cells.map(_.coordinate) :+ coordinate,
        Some(coordinate),
        Some(coordinate)
      )
    }

  def select(item: S): Unit =
    if (!table.isDisposed)
      select((0 until count).find(i => table.items.itemAt(i).contains(item)).getOrElse(-1))

  def clearAndSelect(index: Int): Unit =
    if (cellSelectionEnabled)
      activeColumn.fold(clearSelection())(clearAndSelect(index, _))
    else publishRows(Vector(index), index, index)
  def clearAndSelect(index: Int, column: TableColumn[S, ?]): Unit =
    if (!cellSelectionEnabled) clearAndSelect(index)
    else {
      val coordinate = Coordinate(index, column)
      publishCells(Vector(coordinate), Some(coordinate), Some(coordinate))
    }
  def clearSelection(): Unit =
    if (cellSelectionEnabled) publishCells(Vector.empty, None, None)
    else publishRows(Vector.empty, -1, -1)
  def clearSelection(index: Int): Unit =
    if (isSelected(index)) {
      if (cellSelectionEnabled)
        publishCells(
          state.get.cells.map(_.coordinate).filterNot(_.row == index),
          state.get.cellLead,
          state.get.cellAnchor
        )
      else publishRows(state.get.indices.filterNot(_ == index), state.get.lead, state.get.anchor)
    }
  def clearSelection(index: Int, column: TableColumn[S, ?]): Unit =
    if (!cellSelectionEnabled) clearSelection(index)
    else if (isSelected(index, column))
      publishCells(
        state.get.cells.map(_.coordinate).filterNot(_ == Coordinate(index, column)),
        state.get.cellLead,
        state.get.cellAnchor
      )

  /** Add valid indices atomically; the last valid argument becomes the lead. In cell-selection mode
    * row APIs address the focused column, or the first visible leaf when no cell is focused.
    */
  def selectIndices(indices: Int*): Unit = {
    if (table.isDisposed) return
    val accepted = indices.filter(valid)
    accepted.lastOption.foreach { last =>
      if (cellSelectionEnabled)
        activeColumn.foreach { column =>
          val added = accepted.map(Coordinate(_, column))
          val lead  = Coordinate(last, column)
          publishCells(state.get.cells.map(_.coordinate) ++ added, Some(lead), Some(lead))
        }
      else publishRows(state.get.indices ++ accepted, last, last)
    }
  }

  /** Inclusive start, exclusive end, in either direction. Clip before allocating the range. */
  def selectRange(start: Int, end: Int): Unit = {
    if (table.isDisposed) return
    val range = boundedRange(start, end)
    if (range.nonEmpty) {
      if (cellSelectionEnabled)
        activeColumn.foreach { column =>
          publishCells(
            state.get.cells.map(_.coordinate) ++ range.map(Coordinate(_, column)),
            Some(Coordinate(range.last, column)),
            Some(Coordinate(range.head, column))
          )
        }
      else publishRows(state.get.indices ++ range, range.last, range.head)
    }
  }

  /** Inclusive rectangular cell range, in either row and visible-column direction. */
  def selectRange(
      startRow: Int,
      startColumn: TableColumn[S, ?],
      endRow: Int,
      endColumn: TableColumn[S, ?]
  ): Unit = {
    if (!cellSelectionEnabled) {
      if (valid(startRow) && valid(endRow)) {
        val rows = math.min(startRow, endRow) to math.max(startRow, endRow)
        publishRows(state.get.indices ++ rows, endRow, startRow)
      }
    } else {
      val start = Coordinate(startRow, startColumn)
      val end   = Coordinate(endRow, endColumn)
      val range = rectangle(start, end)
      if (range.nonEmpty)
        publishCells(state.get.cells.map(_.coordinate) ++ range, Some(end), Some(start))
    }
  }

  private def boundedRange(start: Int, end: Int): Range =
    if (start <= end) math.max(0, start) until math.min(count, end)
    else math.min(count - 1, start) until math.max(-1, end) by -1

  private def rectangle(start: Coordinate, end: Coordinate): Vector[Coordinate] = {
    val columns    = table.visibleLeafColumns.get
    val startIndex = columns.indexOf(start.column)
    val endIndex   = columns.indexOf(end.column)
    if (!valid(start.row) || !valid(end.row) || startIndex < 0 || endIndex < 0) Vector.empty
    else {
      val rows = math.min(start.row, end.row) to math.max(start.row, end.row)
      val cols = columns.slice(math.min(startIndex, endIndex), math.max(startIndex, endIndex) + 1)
      rows.iterator.flatMap(row => cols.iterator.map(Coordinate(row, _))).toVector
    }
  }

  /** Select the currently known coordinate space without fetching missing remote values. */
  def selectAll(): Unit =
    if (!table.isDisposed && selectionMode == TableSelectionMode.Multiple) {
      if (cellSelectionEnabled) {
        val columns = table.visibleLeafColumns.get
        val all     = (0 until count).iterator
          .flatMap(row => columns.iterator.map(Coordinate(row, _)))
          .toVector
        publishCells(all, all.lastOption, all.headOption)
      } else publishRows(0 until count, count - 1, 0)
    }

  def selectFirst(): Unit = if (count > 0 && !isSelected(0)) select(0)
  def selectLast(): Unit  = if (count > 0 && !isSelected(count - 1)) select(count - 1)
  def selectNext(): Unit  = {
    val next = state.get.lead + 1
    if (valid(next)) select(next)
  }
  def selectPrevious(): Unit = {
    val previous = if (state.get.lead < 0) count - 1 else state.get.lead - 1
    if (valid(previous)) select(previous)
  }

  protected[table] def click(
      index: Int,
      toggle: Boolean,
      extend: Boolean,
      fallbackAnchor: Int = -1
  ): Unit = {
    if (cellSelectionEnabled) {
      activeColumn.foreach(column =>
        clickCell(index, column, toggle, extend, fallbackAnchor, column)
      )
      return
    }
    if (table.isDisposed || !valid(index)) return
    val anchor = if (valid(state.get.anchor)) state.get.anchor else fallbackAnchor
    if (selectionMode == TableSelectionMode.Single) clearAndSelect(index)
    else if (extend && valid(anchor)) {
      val range = math.min(anchor, index) to math.max(anchor, index)
      publishRows(if (toggle) state.get.indices ++ range else range, index, anchor)
    } else if (toggle && isSelected(index)) {
      publishRows(state.get.indices.filterNot(_ == index), state.get.lead, index)
    } else if (toggle) select(index)
    else clearAndSelect(index)
  }

  protected[table] def clickCell(
      index: Int,
      column: TableColumn[S, ?],
      toggle: Boolean,
      extend: Boolean,
      fallbackRow: Int = -1,
      fallbackColumn: TableColumn[S, ?] | Null = null
  ): Unit = {
    if (!cellSelectionEnabled) { click(index, toggle, extend, fallbackRow); return }
    if (table.isDisposed || !valid(index) || !visible(column)) return
    val target = Coordinate(index, column)
    val anchor = state.get.cellAnchor
      .filter(current => valid(current.row) && visible(current.column))
      .orElse(
        Option(fallbackColumn)
          .filter(visible)
          .filter(_ => valid(fallbackRow))
          .map(Coordinate(fallbackRow, _))
      )
    if (selectionMode == TableSelectionMode.Single) clearAndSelect(index, column)
    else if (extend && anchor.nonEmpty) {
      val range = rectangle(anchor.get, target)
      publishCells(
        if (toggle) state.get.cells.map(_.coordinate) ++ range else range,
        Some(target),
        anchor
      )
    } else if (toggle && isSelected(index, column)) {
      publishCells(
        state.get.cells.map(_.coordinate).filterNot(_ == target),
        state.get.cellLead,
        Some(target)
      )
    } else if (toggle) select(index, column)
    else clearAndSelect(index, column)
  }

  protected[table] def refresh(): Unit = {
    val previous = state.get
    if (previous.cellSelectionEnabled)
      publishCells(previous.cells.map(_.coordinate), previous.cellLead, previous.cellAnchor)
    else publishRows(previous.indices, previous.lead, previous.anchor)
  }

  /** Drop hidden/removed cells and republish visible indices after column reordering. */
  protected[table] def reconcileColumns(): Unit = {
    val previous = state.get
    if (previous.cellSelectionEnabled)
      publishCells(
        previous.cells.map(_.coordinate),
        previous.cellLead,
        previous.cellAnchor,
        force = true
      )
  }

  /** Rebase selected occurrences and Shift anchors using absolute structural coordinates. */
  protected[table] def reconcile(change: ListDataSource.Change[S]): Unit = {
    if (table.isDisposed) return
    val previous                                                        = state.get
    def splice(index: Int, from: Int, removed: Int, inserted: Int): Int =
      if (index < 0 || index < from) index
      else if (index < from + removed) -1
      else index + inserted - removed
    val remap: Int => Int = change match {
      case ListDataSource.Insert(index, _, _)           => i => splice(i, index, 0, 1)
      case ListDataSource.InsertAll(index, values, _)   => i => splice(i, index, 0, values.length)
      case ListDataSource.RemoveAt(index, _, _)         => i => splice(i, index, 1, 0)
      case ListDataSource.RemoveRange(index, values, _) => i => splice(i, index, values.length, 0)
      case ListDataSource.Patch(from, removed, inserted, _) =>
        i => splice(i, from, removed.length, inserted.length)
      case ListDataSource.Clear(_, _) => _ => -1
      case ListDataSource.Reset(_)    => resetMapping(previous, allowReferenceFallback = true)
      case _                          => identity
    }
    publishRemapped(previous, remap)
  }

  /** Accepted remote replacements invalidate positions. A configured row key may resolve the same
    * loaded entities at their new positions; without one, remote resets clear the model.
    */
  protected[table] def reconcileReset(allowReferenceFallback: Boolean): Unit = {
    if (table.isDisposed) return
    val previous = state.get
    publishRemapped(previous, resetMapping(previous, allowReferenceFallback))
  }

  private def publishRemapped(previous: Snapshot, remap: Int => Int): Unit =
    if (previous.cellSelectionEnabled)
      publishCells(
        previous.cells.map(cell => cell.coordinate.copy(row = remap(cell.coordinate.row))),
        previous.cellLead.map(current => current.copy(row = remap(current.row))),
        previous.cellAnchor.map(current => current.copy(row = remap(current.row)))
      )
    else publishRows(previous.indices.map(remap), remap(previous.lead), remap(previous.anchor))

  private def resetMapping(
      previous: Snapshot,
      allowReferenceFallback: Boolean
  ): Int => Int = table.rowKeyProperty.get match {
    case Some(rowKey) =>
      val entryKeys = previous.entries
        .map(entry => entry.index -> entry.item.flatMap(TableRowIdentity.keyOf(_, rowKey)))
        .toMap
      val duplicateKeys = entryKeys.values.flatten
        .groupBy(identity)
        .collect { case (key, occurrences) if occurrences.size > 1 => key }
        .toSet
      val anchorKey = previous.anchorItem.flatMap(TableRowIdentity.keyOf(_, rowKey))
      val locations = TableRowIdentity.locate(
        table.items,
        rowKey,
        (entryKeys.values.flatten ++ anchorKey).toSet
      )
      def locate(key: Option[TableRowIdentity.Key]): Int =
        key.filterNot(duplicateKeys).flatMap(locations.get).getOrElse(-1)
      index =>
        if (index == previous.anchor) locate(anchorKey)
        else locate(entryKeys.getOrElse(index, None))
    case None if allowReferenceFallback =>
      val positions = new java.util.IdentityHashMap[AnyRef, java.lang.Integer]()
      (previous.entries.flatMap(_.item) ++ previous.anchorItem).foreach { item =>
        positions.put(item.asInstanceOf[AnyRef], -1)
      }
      if (!positions.isEmpty) (0 until count).foreach { index =>
        table.items.itemAt(index).foreach { item =>
          val key = item.asInstanceOf[AnyRef]
          if (positions.containsKey(key)) {
            val found = positions.get(key).intValue
            positions.put(key, if (found == -1) index else -2)
          }
        }
      }
      def locate(item: Option[S]): Int = item.fold(-1)(value =>
        Option(positions.get(value.asInstanceOf[AnyRef])).fold(-1)(_.intValue)
      )
      val mapping = previous.entries.map(entry => entry.index -> locate(entry.item)).toMap
      index =>
        if (index == previous.anchor) locate(previous.anchorItem) else mapping.getOrElse(index, -1)
    case None => _ => -1
  }

  private def sameItem(a: Option[S], b: Option[S]): Boolean = (a, b) match {
    case (Some(x), Some(y)) => x.asInstanceOf[AnyRef] eq y.asInstanceOf[AnyRef]
    case (None, None)       => true
    case _                  => false
  }

  private def publishRows(
      requested: Iterable[Int],
      lead: Int,
      anchor: Int,
      mode: TableSelectionMode = state.get.mode,
      enabled: Boolean = state.get.cellSelectionEnabled,
      force: Boolean = false
  ): Unit = {
    if (table.isDisposed) return
    if (enabled) {
      activeColumn match {
        case Some(column) =>
          val coordinates = requested.iterator.map(Coordinate(_, column)).toVector
          publishCells(
            coordinates,
            Option.when(valid(lead))(Coordinate(lead, column)),
            Option.when(valid(anchor))(Coordinate(anchor, column)),
            mode,
            enabled = true,
            force
          )
        case None => publishCells(Vector.empty, None, None, mode, enabled = true, force)
      }
      return
    }
    val indices  = requested.iterator.filter(valid).toVector.distinct.sorted
    val nextLead = if (indices.contains(lead)) lead else indices.lastOption.getOrElse(-1)
    val selected = if (mode == TableSelectionMode.Single) indices.filter(_ == nextLead) else indices
    val nextAnchor = if (valid(anchor)) anchor else -1
    val next       = Snapshot(
      mode,
      false,
      selected.map(i => Entry(i, table.items.itemAt(i))),
      nextLead,
      nextAnchor,
      if (nextAnchor >= 0) table.items.itemAt(nextAnchor) else None,
      Vector.empty,
      None,
      None
    )
    commit(next, force)
  }

  private def publishCells(
      requested: Iterable[Coordinate],
      lead: Option[Coordinate],
      anchor: Option[Coordinate],
      mode: TableSelectionMode = state.get.mode,
      enabled: Boolean = state.get.cellSelectionEnabled,
      force: Boolean = false
  ): Unit = {
    if (table.isDisposed) return
    val coordinates = requested.iterator
      .filter(current => valid(current.row) && visible(current.column))
      .toVector
      .distinct
      .sortBy(current => (current.row, table.getVisibleLeafIndex(current.column)))
    val nextLead = lead.filter(coordinates.contains).orElse(coordinates.lastOption)
    val selected = if (mode == TableSelectionMode.Single) nextLead.toVector else coordinates
    val rows     = selected
      .groupBy(_.row)
      .toVector
      .sortBy(_._1)
      .map((row, _) => Entry(row, table.items.itemAt(row)))
    val nextAnchor = anchor.filter(current => valid(current.row) && visible(current.column))
    val next       = Snapshot(
      mode,
      enabled,
      rows,
      nextLead.fold(-1)(_.row),
      nextAnchor.fold(-1)(_.row),
      nextAnchor.flatMap(current => table.items.itemAt(current.row)),
      selected.map(current => CellEntry(current, table.items.itemAt(current.row))),
      nextLead,
      nextAnchor
    )
    commit(next, force)
  }

  private def commit(next: Snapshot, force: Boolean): Unit = {
    val old       = state.get
    val unchanged =
      old.mode == next.mode && old.cellSelectionEnabled == next.cellSelectionEnabled &&
        old.lead == next.lead && old.anchor == next.anchor && old.indices == next.indices &&
        old.cells.map(_.coordinate) == next.cells.map(
          _.coordinate
        ) && old.cellLead == next.cellLead &&
        old.cellAnchor == next.cellAnchor && sameItem(old.anchorItem, next.anchorItem) &&
        old.entries.zip(next.entries).forall((a, b) => sameItem(a.item, b.item)) &&
        old.cells.zip(next.cells).forall((a, b) => sameItem(a.item, b.item))
    if (force || !unchanged) state.setAlways(next)
  }
}
