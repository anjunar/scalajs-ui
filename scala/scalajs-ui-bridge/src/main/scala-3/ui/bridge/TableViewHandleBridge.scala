package ui.bridge

import ui.control.table.{ColumnResizePolicy, TableSelectionMode, TableSort, TableView}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Minimal imperative table contract; all operations remain owned by the Scala component. */
final class TableViewHandleBridge(private val table: TableView[js.Any]) extends js.Object {
  val visibleColumnCount = new ReadOnlyPropertyHandle(table.visibleLeafColumns.map(_.size))
  def getCellData(rowIndex: Double, columnIndex: Double): js.Any | Null =
    if (!validIndex(rowIndex) || !validIndex(columnIndex)) null
    else
      Option(table.getVisibleLeafColumn(columnIndex.toInt))
        .map(_.asInstanceOf[ui.control.table.TableColumn[js.Any, js.Any]])
        .fold[js.Any | Null](null)(_.getCellData(rowIndex.toInt))
  def getCellObservableValue(
      rowIndex: Double,
      columnIndex: Double
  ): ReadOnlyPropertyHandle[js.Any] | Null =
    if (!validIndex(rowIndex) || !validIndex(columnIndex)) null
    else
      Option(table.getVisibleLeafColumn(columnIndex.toInt))
        .map(_.asInstanceOf[ui.control.table.TableColumn[js.Any, js.Any]])
        .flatMap(column => Option(column.getCellObservableValue(rowIndex.toInt)))
        .fold[ReadOnlyPropertyHandle[js.Any] | Null](null)(new ReadOnlyPropertyHandle(_))

  def setSortOrder(order: js.Array[TableSortFacade]): Boolean =
    if (order == null || !js.Array.isArray(order)) false
    else {
      val parsed = order.iterator.map { term =>
        if (term == null || js.typeOf(term) != "object") None
        else {
          // Keep untrusted fields as js.Any until validation. Reading a native Boolean first
          // would coerce an absent field to false before js.typeOf can inspect it.
          val index     = term.columnIndex
          val ascending = term.ascending
          if (
            js.typeOf(index) != "number" || !validIndex(index.asInstanceOf[Double]) ||
            js.typeOf(ascending) != "boolean"
          ) None
          else
            Some(
              TableSort(
                table.getVisibleLeafColumn(index.asInstanceOf[Double].toInt),
                ascending.asInstanceOf[Boolean]
              )
            )
        }
      }.toVector
      parsed.forall(_.nonEmpty) && table.setSortOrder(parsed.flatten)
    }
  def sort(): Boolean = table.sort()
  val sorting         = new ReadOnlyPropertyHandle(
    table.sortingProperty.map(
      _.map(sort => js.Dynamic.literal(field = sort.field, ascending = sort.ascending)).toJSArray
    )
  )
  def toggleSort(index: Double, additive: js.UndefOr[Boolean]): Boolean =
    validIndex(index) && table.toggleSort(
      table.getVisibleLeafColumn(index.toInt),
      additive.getOrElse(false)
    )
  def clearSort(): Boolean = table.clearSort()
  private def model        = table.selectionModel
  val focusedIndex         = new ReadOnlyPropertyHandle(table.focusedIndexProperty)
  val focusedItem          = new ReadOnlyPropertyHandle(table.focusedItemProperty)
  val focusedCell          = new ReadOnlyPropertyHandle(
    table.focusedCellProperty.map(position =>
      Option(position).fold[js.Object | Null](null)(current =>
        js.Dynamic.literal(row = current.row, column = current.column)
      )
    )
  )
  val editingCell = new ReadOnlyPropertyHandle(
    table.editingCellProperty.map(position =>
      Option(position).fold[js.Object | Null](null)(current =>
        js.Dynamic.literal(row = current.row, column = current.column)
      )
    )
  )
  val editingItem       = new ReadOnlyPropertyHandle(table.editingItemProperty)
  val originalEditValue = new ReadOnlyPropertyHandle(table.originalEditValueProperty)
  val editingValue      = new ReadOnlyPropertyHandle(table.editingValueProperty)
  def editCell(rowIndex: Double, columnIndex: Double): Boolean =
    columnAt(columnIndex).exists(column =>
      validIndex(rowIndex) && table.edit(
        rowIndex.toInt,
        column.asInstanceOf[ui.control.table.TableColumn[js.Any, js.Any]]
      )
    )
  def updateEdit(value: js.Any): Boolean             = table.updateEdit(value)
  def commitEdit(value: js.UndefOr[js.Any]): Boolean =
    value.fold(table.commitEdit())(table.commitEdit)
  def cancelEdit(): Boolean           = table.cancelEdit()
  def focusIndex(index: Double): Unit =
    table.focusModel.focus(if (validIndex(index)) index.toInt else -1)
  def focusCell(rowIndex: Double, columnIndex: Double): Unit =
    if (validIndex(rowIndex) && validIndex(columnIndex))
      table.focusModel.focus(rowIndex.toInt, table.getVisibleLeafColumn(columnIndex.toInt))
    else table.focusModel.focus(-1)
  def focusNext(): Unit      = table.focusModel.focusNext()
  def focusPrevious(): Unit  = table.focusModel.focusPrevious()
  def focusLeftCell(): Unit  = table.focusModel.focusLeftCell()
  def focusRightCell(): Unit = table.focusModel.focusRightCell()
  def focusAboveCell(): Unit = table.focusModel.focusAboveCell()
  def focusBelowCell(): Unit = table.focusModel.focusBelowCell()
  val columnWidths = new ReadOnlyPropertyHandle(table.renderedWidthsProperty.map(_.toJSArray))
  def autoFitColumn(index: Double): Boolean =
    validIndex(index) && table.autoFitColumn(table.getVisibleLeafColumn(index.toInt))
  def moveColumn(from: Double, to: Double): Boolean =
    validIndex(from) && validIndex(to) && table.moveColumn(
      table.getVisibleLeafColumn(from.toInt),
      to.toInt
    )
  def resizeColumn(index: Double, delta: Double): Boolean =
    validIndex(index) && table.resizeColumn(table.getVisibleLeafColumn(index.toInt), delta)
  val selectionMode = new ReadOnlyPropertyHandle(
    table.selectionModelProperty.flatMap(_.selectionModeProperty).map {
      case TableSelectionMode.Single   => "single"
      case TableSelectionMode.Multiple => "multiple"
    }
  )
  val cellSelectionEnabled = new ReadOnlyPropertyHandle(
    table.selectionModelProperty.flatMap(_.cellSelectionEnabledProperty)
  )
  val selectedCells = new ReadOnlyPropertyHandle(
    table.selectedCellsProperty.map(
      _.map(position => js.Dynamic.literal(row = position.row, column = position.column)).toJSArray
    )
  )
  val selectedIndices = new ReadOnlyPropertyHandle(table.selectedIndicesProperty.map(_.toJSArray))
  val selectedItems   = new ReadOnlyPropertyHandle(table.selectedItemsProperty.map(_.toJSArray))
  private def validIndex(index: Double): Boolean =
    index.isWhole && index >= 0 && index <= Int.MaxValue
  val selectedIndex                    = new ReadOnlyPropertyHandle(table.selectedIndexProperty)
  val selectedItem                     = new ReadOnlyPropertyHandle(table.selectedItemProperty)
  def selectIndex(index: Double): Unit =
    if (validIndex(index)) table.select(index.toInt)
    else table.clearSelection()
  def selectItem(item: js.Any): Unit       = table.select(item)
  def clearSelection(): Unit               = table.clearSelection()
  def setSelectionMode(mode: String): Unit =
    if (!table.isDisposed) model.selectionMode = TableViewHandleBridge.parseSelectionMode(mode)
  def setCellSelectionEnabled(enabled: Boolean): Unit =
    if (!table.isDisposed) model.cellSelectionEnabled = enabled
  def clearAndSelect(index: Double): Unit =
    if (validIndex(index)) model.clearAndSelect(index.toInt) else model.clearSelection()
  def clearIndex(index: Double): Unit    = if (validIndex(index)) model.clearSelection(index.toInt)
  def isSelected(index: Double): Boolean = validIndex(index) && model.isSelected(index.toInt)
  private def columnAt(index: Double)    =
    if (validIndex(index)) Option(table.getVisibleLeafColumn(index.toInt)) else None
  def selectCell(rowIndex: Double, columnIndex: Double): Unit =
    columnAt(columnIndex) match {
      case Some(column) if validIndex(rowIndex) => model.select(rowIndex.toInt, column)
      case _                                    => model.clearSelection()
    }
  def clearAndSelectCell(rowIndex: Double, columnIndex: Double): Unit =
    columnAt(columnIndex) match {
      case Some(column) if validIndex(rowIndex) => model.clearAndSelect(rowIndex.toInt, column)
      case _                                    => model.clearSelection()
    }
  def clearCell(rowIndex: Double, columnIndex: Double): Unit =
    columnAt(columnIndex).foreach(column =>
      if (validIndex(rowIndex)) model.clearSelection(rowIndex.toInt, column)
    )
  def isCellSelected(rowIndex: Double, columnIndex: Double): Boolean =
    validIndex(rowIndex) && columnAt(columnIndex).exists(model.isSelected(rowIndex.toInt, _))
  def selectCellRange(
      startRow: Double,
      startColumn: Double,
      endRow: Double,
      endColumn: Double
  ): Unit =
    for {
      first <- columnAt(startColumn)
      last  <- columnAt(endColumn)
      if validIndex(startRow) && validIndex(endRow)
    } model.selectRange(startRow.toInt, first, endRow.toInt, last)
  def selectIndices(indices: js.Array[Double]): Unit =
    if (!table.isDisposed)
      model.selectIndices(indices.iterator.filter(validIndex).map(_.toInt).toSeq*)
  def selectRange(start: Double, end: Double): Unit =
    if (
      start.isWhole && end.isWhole && start >= Int.MinValue && start <= Int.MaxValue &&
      end >= Int.MinValue && end <= Int.MaxValue
    ) model.selectRange(start.toInt, end.toInt)
  def selectAll(): Unit                        = model.selectAll()
  def selectFirst(): Unit                      = model.selectFirst()
  def selectLast(): Unit                       = model.selectLast()
  def selectNext(): Unit                       = model.selectNext()
  def selectPrevious(): Unit                   = model.selectPrevious()
  def scrollToIndex(index: Double): Unit       = if (validIndex(index)) table.scrollTo(index.toInt)
  def scrollToItem(item: js.Any): Unit         = table.scrollTo(item)
  def scrollToColumnIndex(index: Double): Unit =
    if (validIndex(index)) table.scrollToColumnIndex(index.toInt)
  def isDisposed: Boolean = table.isDisposed
  def refresh(): Unit     = table.refresh()
}

@js.native
trait TableSortFacade extends js.Object {
  val columnIndex: js.Any = js.native
  val ascending: js.Any   = js.native
}

private[bridge] object TableViewHandleBridge {
  def parseResizePolicy(policy: String): ColumnResizePolicy = policy match {
    case "unconstrained"      => ColumnResizePolicy.Unconstrained
    case "all-columns"        => ColumnResizePolicy.AllColumns
    case "last-column"        => ColumnResizePolicy.LastColumn
    case "next-column"        => ColumnResizePolicy.NextColumn
    case "subsequent-columns" => ColumnResizePolicy.SubsequentColumns
    case "flex-next-column"   => ColumnResizePolicy.FlexNextColumn
    case "flex-last-column"   => ColumnResizePolicy.FlexLastColumn
    case _ => throw new IllegalArgumentException(s"Unknown column resize policy: $policy")
  }
  def parseSelectionMode(mode: String): TableSelectionMode = mode match {
    case "single"   => TableSelectionMode.Single
    case "multiple" => TableSelectionMode.Multiple
    case _ => throw new IllegalArgumentException("Selection mode must be 'single' or 'multiple'")
  }
}
