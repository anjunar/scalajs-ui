package ui.control.table

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.{addClass, classIf}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.{onClick, onDoubleClick}
import ui.core.dsl.StyleDsl.*
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.{Disposable, Property, ReadOnlyProperty}

class TableCell[S, T] extends AbstractComponent {
  override val tagName: String = "div"

  val itemProperty: Property[T | Null]              = Property(null)
  val emptyProperty: Property[Boolean]              = Property(true)
  val editableProperty: Property[Boolean]           = Property(true)
  private val editingState: Property[Boolean]       = Property(false)
  val editingProperty: ReadOnlyProperty[Boolean]    = editingState
  private val focusedState: Property[Boolean]       = Property(false)
  val focusedProperty: ReadOnlyProperty[Boolean]    = focusedState
  private val selectedState: Property[Boolean]      = Property(false)
  val selectedProperty: ReadOnlyProperty[Boolean]   = selectedState
  private val disabledState: Property[Boolean]      = Property(false)
  val disabledProperty: ReadOnlyProperty[Boolean]   = disabledState
  private val indexState: Property[Int]             = Property(-1)
  val indexProperty: ReadOnlyProperty[Int]          = indexState
  private var boundRow: TableRow[S] | Null          = null
  private var boundColumn: TableColumn[S, T] | Null = null
  private var valueSubscription: Disposable         = Disposable.empty

  def editable: Boolean                     = editableProperty.get
  def editable_=(value: Boolean): Unit      = editableProperty.set(value)
  def editing: Boolean                      = editingProperty.get
  def tableRow: TableRow[S] | Null          = boundRow
  def tableColumn: TableColumn[S, T] | Null = boundColumn
  def tableView: TableView[S] | Null        =
    Option(boundColumn).fold[TableView[S] | Null](null)(_.tableViewProperty.get)

  private[control] def bind(row: TableRow[S], column: TableColumn[S, T]): Unit = {
    require(
      boundRow == null && !isBound && !isDisposed,
      "A cell factory must return a fresh, unmounted TableCell"
    )
    boundRow = row
    boundColumn = column
    indexState.set(row.indexProperty.get)
    emptyProperty.set(row.isPlaceholder)
  }

  override final def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("ui-table-cell")
      setAttribute("role", "gridcell")
      classIf("ui-table-cell-empty", emptyProperty)
      for (column <- Option(boundColumn); table <- Option(tableView)) {
        table.registerCell(this)
        addDisposable(TableColumn.applyClasses(this, column.cellClassesProperty))
        addDisposable(
          table.focusedCellProperty.observe(position =>
            focusedState.set(
              Option(position).exists(current =>
                current.row == indexProperty.get && (current.tableColumn eq column)
              )
            )
          )
        )
        classIf("ui-table-cell-focused", focusedProperty)
        addDisposable(
          table.editingCellProperty.observe(position =>
            editingState.set(
              Option(position).exists(current =>
                current.row == indexProperty.get && (current.tableColumn eq column)
              )
            )
          )
        )
        classIf("ui-table-cell-editing", editingProperty)
        def updateEditableState(): Unit =
          setAttribute(
            "aria-readonly",
            (!table.editableProperty.get || !table.isColumnEditable(column) ||
              !editableProperty.get).toString
          )
        updateEditableState()
        addDisposable(table.editableProperty.observeWithoutInitial(_ => updateEditableState()))
        addDisposable(
          table.columnEditabilityRevisionProperty.observeWithoutInitial(_ => updateEditableState())
        )
        addDisposable(editableProperty.observeWithoutInitial { _ =>
          updateEditableState()
          table.editModel.cellEditableChanged(this)
        })
        addDisposable(
          table.selectedCellsProperty.observe(_ =>
            selectedState.set(
              table.selectionModel.cellSelectionEnabled &&
                table.selectionModel.isSelected(indexProperty.get, column)
            )
          )
        )
        classIf("ui-table-cell-selected", selectedProperty)
        addDisposable(
          selectedProperty.observe(selected => setAttribute("aria-selected", selected.toString))
        )
        // V05: a cell inherits its row's disabled state -- there is no independent per-cell
        // predicate, matching how JavaFX's own Node.disable cascades from a disabled row to its
        // cells. Stays visible and reachable by keyboard focus; cannot be selected or edited.
        Option(boundRow).foreach { row =>
          addDisposable(row.disabledProperty.observe(disabledState.set))
        }
        classIf("ui-table-cell-disabled", disabledProperty)
        addDisposable(
          disabledProperty.observe(value => setAttribute("aria-disabled", value.toString))
        )
        onClick { event =>
          event.raw match {
            case mouse: org.scalajs.dom.MouseEvent if !emptyProperty.get && !disabledProperty.get =>
              val element = host
                .asInstanceOf[ui.core.render.DomHostElement]
                .node
                .asInstanceOf[org.scalajs.dom.Element]
              if (TableRowKeyboard.isRowBackground(mouse, element) && table.canMoveColumns) {
                mouse.stopPropagation()
                if (table.selectionModel.cellSelectionEnabled)
                  table.selectionModel.clickCell(
                    indexProperty.get,
                    column,
                    mouse.ctrlKey || mouse.metaKey,
                    mouse.shiftKey
                  )
                else
                  table.selectionModel.click(
                    indexProperty.get,
                    mouse.ctrlKey || mouse.metaKey,
                    mouse.shiftKey
                  )
                table.focusCellFromPointer(indexProperty.get, column)
              }
            case _ => ()
          }
        }
        if (supportsIntegratedEditor)
          onDoubleClick { event =>
            event.raw match {
              case mouse: org.scalajs.dom.MouseEvent
                  if !emptyProperty.get && !disabledProperty.get =>
                val element = host
                  .asInstanceOf[ui.core.render.DomHostElement]
                  .node
                  .asInstanceOf[org.scalajs.dom.Element]
                if (TableRowKeyboard.isRowBackground(mouse, element) && startIntegratedEdit()) {
                  mouse.preventDefault()
                  mouse.stopPropagation()
                }
              case _ => ()
            }
          }
        addDisposable(
          table.visibleLeafColumns.observe(_ =>
            if (!isDisposed)
              setAttribute("aria-colindex", (table.getVisibleLeafIndex(column) + 1).toString)
          )
        )
        classIf("ui-table-cell-last", table.visibleLeafColumns.map(_.lastOption.contains(column)))
        if (emptyProperty.get) addClass("ui-table-cell-loading-placeholder")
        val widthProperty = table.renderedWidthsProperty.map { widths =>
          s"${widths.lift(table.getVisibleLeafIndex(column)).getOrElse(column.prefWidth)}px"
        }
        style {
          width = widthProperty
          minWidth = widthProperty
          flex = "0 0 auto"
        }
        addDisposable(Disposable(valueSubscription.dispose()))
        addDisposable(column.cellValueFactoryProperty.observe(_ => bindValue()))
      }
      if (!emptyProperty.get || boundRow == null) renderContent(using this, cursor)
    }

  /** Override content, not composition, so table binding and disposal remain owned by the cell. */
  protected def renderContent(using AbstractComponent, Cursor): Unit =
    Option(boundColumn).flatMap(_.cellRenderer.get) match {
      case Some(renderer) =>
        Option(boundRow).foreach(row => renderer(row.itemProperty.get.asInstanceOf[S]))
      case None => text(itemProperty.map(item => Option(item).fold("")(_.toString))) {}
    }

  /** Marks cells whose editor can be entered from the table keyboard or a double click. */
  private[table] def supportsIntegratedEditor: Boolean = false

  private[table] final def startIntegratedEdit(): Boolean =
    (for {
      table  <- Option(tableView)
      column <- Option(boundColumn)
      if supportsIntegratedEditor && !emptyProperty.get
    } yield {
      val started = table.edit(indexProperty.get, column)
      if (started) table.focusModel.focus(indexProperty.get, column)
      started
    }).getOrElse(false)

  private def bindValue(): Unit = {
    valueSubscription.dispose()
    valueSubscription = Disposable.empty
    val value = for {
      row      <- Option(boundRow) if !row.isPlaceholder
      column   <- Option(boundColumn)
      property <- Option(
        column.observableValue(row.itemProperty.get.asInstanceOf[S], row.indexProperty.get)
      )
    } yield property
    value match {
      case Some(property) => valueSubscription = property.observe(itemProperty.setAlways)
      case None           => itemProperty.set(null)
    }
  }

  private[control] def applyRenderedItem(item: T | Null, empty: Boolean): Unit = {
    itemProperty.set(item)
    emptyProperty.set(empty)
  }
}

object TableCell {
  def cellEditable[S, T](using cell: TableCell[S, T]): Boolean = cell.editableProperty.get
  def cellEditable_=[S, T](value: Boolean)(using cell: TableCell[S, T]): Unit =
    cell.editableProperty.set(value)
  def cellEditable_=[S, T](value: ReadOnlyProperty[Boolean])(using cell: TableCell[S, T]): Unit =
    cell.addDisposable(value.observe(cell.editableProperty.set))

  def editing[S, T](using cell: TableCell[S, T]): Boolean = cell.editingProperty.get

  /** The enclosing cell, for a lightweight `cell { item => ... }` renderer body (D05). That body
    * only receives the item, but the ambient `AbstractComponent` given during it IS the `TableCell`
    * itself (`compose` calls `renderContent(using this, cursor)`), so this is a safe projection of
    * already-existing state, not a new channel -- a `cellFactory` subclass already has the same
    * `index`/`empty`/`selected`/`focused`/`editing` properties directly via `this`.
    */
  def enclosingCell(using component: AbstractComponent): TableCell[?, ?] =
    component.asInstanceOf[TableCell[?, ?]]

  def cell[S, T](
      body: TableCell[S, T] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): TableCell[S, T] =
    DslLayer.child(new TableCell[S, T]()) {
      body
    }

  def cell[S, T]()(using AbstractComponent, Cursor): TableCell[S, T] =
    DslLayer.child(new TableCell[S, T]()) {}
}
