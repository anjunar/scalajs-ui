package ui.control.table

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.{addClass, classIf}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.{onClick, onDoubleClick}
import ui.core.dsl.StyleDsl.*
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.{Disposable, Property, ReadOnlyProperty}
import ui.core.statement.DynamicComponentRenderer.dynamic
import org.scalajs.dom

class TableRow[S] private[control] (
    initialize: TableRow[S] ?=> Cursor ?=> Unit
) extends AbstractComponent {
  def this() = this((_: TableRow[S]) ?=> (_: Cursor) ?=> ())

  override val tagName: String = "div"

  private val itemState                           = Property[S | Null](null)
  private val indexState                          = Property(-1)
  private val emptyState                          = Property(true)
  private val selectedState                       = Property(false)
  private val focusedState                        = Property(false)
  private val disabledState                       = Property(false)
  val focusedProperty: ReadOnlyProperty[Boolean]  = focusedState
  val itemProperty: ReadOnlyProperty[S | Null]    = itemState
  val indexProperty: ReadOnlyProperty[Int]        = indexState
  val emptyProperty: ReadOnlyProperty[Boolean]    = emptyState
  val selectedProperty: ReadOnlyProperty[Boolean] = selectedState
  val disabledProperty: ReadOnlyProperty[Boolean] = disabledState

  private var ownerTable: TableView[S] | Null = null
  def tableView: TableView[S] | Null          = ownerTable
  private var placeholder                     = false
  private var cellsRendered                   = false
  private[control] def isPlaceholder: Boolean = placeholder

  override final def compose(cursor: Cursor): Unit = {
    initialize(using this)(using cursor)

    DslLayer.render(this, cursor) {
      addClass("ui-table-row")
      setAttribute("role", "row")
      val owner = requireTableView()
      addDisposable(
        owner.headerRowCountProperty.observe(_ =>
          setAttribute(
            "aria-rowindex",
            (indexProperty.get.toLong +
              (if (owner.showHeaderProperty.get) owner.headerRowCountProperty.get + 1
               else 1)).toString
          )
        )
      )
      addDisposable(
        owner.showHeaderProperty.observe(_ =>
          setAttribute(
            "aria-rowindex",
            (indexProperty.get.toLong +
              (if (owner.showHeaderProperty.get) owner.headerRowCountProperty.get + 1
               else 1)).toString
          )
        )
      )
      addDisposable(
        owner.focusedIndexProperty.observe(index => focusedState.set(index == indexProperty.get))
      )
      classIf("ui-table-row-focused", focusedProperty)
      classIf(
        "ui-table-row-cell-focused",
        owner.focusedCellProperty.map(position =>
          Option(position).exists(current =>
            current.row == indexProperty.get && current.tableColumn != null
          )
        )
      )
      if (cursor.isBrowser) owner.registerRow(this)
      if (indexProperty.get % 2 == 0) addClass("ui-table-row-even")
      else addClass("ui-table-row-odd")

      style {
        display = "flex"
        width = "100%"
        height = owner.variableRowHeightProperty.map(variable => if (variable) "auto" else "100%")
        minHeight = owner.rowHeightProperty.map(value => s"${math.max(1.0, value)}px")
        boxSizing = "border-box"
      }

      if (placeholder) {
        addClass("ui-table-row-empty")
        addClass("ui-table-row-placeholder")
        setAttribute("aria-selected", "false")
      } else {
        val table = requireTableView()
        addDisposable(
          table.selectedIndicesProperty.observe(_ =>
            selectedState.set(
              !table.selectionModel.cellSelectionEnabled &&
                table.selectionModel.isSelected(indexProperty.get)
            )
          )
        )
        classIf("ui-table-row-selected", selectedProperty)
        addDisposable(
          selectedProperty.observe(value => setAttribute("aria-selected", value.toString))
        )

        // V05: a disabled row stays visible and keyboard-reachable -- it just cannot be selected,
        // edited, or fire the double-click event. The selection model itself refuses select/edit
        // for a disabled index too (TableSelectionModel.click/clickCell, TableView.canStartEdit),
        // so this is a defense-in-depth short-circuit, not the only guard.
        addDisposable(
          table.rowDisabledProperty
            .flatMap { predicate =>
              itemProperty.map { item =>
                predicate.exists { pred =>
                  item match {
                    case value: S @unchecked => pred(value)
                    case null                => false
                  }
                }
              }
            }
            .observe(disabledState.set)
        )
        classIf("ui-table-row-disabled", disabledProperty)
        addDisposable(
          disabledProperty.observe(value => setAttribute("aria-disabled", value.toString))
        )

        onClick { event =>
          if (!disabledProperty.get) {
            if (cursor.isBrowser && event.raw != null) {
              val mouse   = event.raw.asInstanceOf[dom.MouseEvent]
              val element = host.asInstanceOf[DomHostElement].node.asInstanceOf[dom.Element]
              if (TableRowKeyboard.isRowBackground(mouse, element) && table.canMoveColumns) {
                table.selectionModel.click(
                  indexProperty.get,
                  mouse.ctrlKey || mouse.metaKey,
                  mouse.shiftKey
                )
                table.focusRowFromPointer(indexProperty.get)
              }
            } else table.selectionModel.click(indexProperty.get, toggle = false, extend = false)
          }
        }
        onDoubleClick { _ =>
          if (!disabledProperty.get) itemProperty.get match {
            case item: S @unchecked => table.fireRowDoubleClick(item)
            case null               => ()
          }
        }
      }

      renderContent(using this, cursor)
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) cursor.afterHydration { () =>
      if (!isDisposed) {
        var measurement: Disposable = Disposable.empty
        addDisposable(requireTableView().variableRowHeightProperty.observe { enabled =>
          measurement.dispose()
          measurement = if (enabled) observeHeight() else Disposable.empty
        })
        addDisposable(Disposable(measurement.dispose()))
      }
    }

  private def observeHeight(): Disposable =
    domElement.fold[Disposable](Disposable.empty) { element =>
      var active  = true
      val measure =
        () => if (active) requireTableView().handleMeasuredRowHeight(this, heightOf(element))
      val frame    = dom.window.requestAnimationFrame(_ => measure())
      val observer = new dom.ResizeObserver((_, _) => measure())
      observer.observe(element)
      Disposable {
        active = false
        dom.window.cancelAnimationFrame(frame)
        observer.disconnect()
      }
    }

  private[table] def measuredHeight: Option[Double] =
    domElement.map(heightOf).filter(_ > 0)

  private def heightOf(element: dom.html.Element): Double = {
    val fractional = element.getBoundingClientRect().height
    if (fractional > 0) fractional else element.offsetHeight.toDouble
  }

  private def domElement: Option[dom.html.Element] =
    if (!isBound || isDisposed) None
    else
      host match {
        case domHost: DomHostElement =>
          domHost.node match {
            case element: dom.html.Element => Some(element)
            case _                         => None
          }
        case _ => None
      }

  /** Customize contents while retaining table-owned selection, binding and disposal. */
  protected def renderContent(using AbstractComponent, Cursor): Unit = renderCells

  /** Compose the standard visible cells, optionally inside a custom wrapper. At most once per row.
    */
  protected final def renderCells(using AbstractComponent, Cursor): Unit = {
    require(
      !cellsRendered && !isDisposed,
      "Standard cells can only be rendered once per live TableRow"
    )
    cellsRendered = true
    DslLayer.child(
      new TableColumnProjection(
        requireTableView(),
        column => {
          val typedColumn = column.asInstanceOf[TableColumn[S, Any]]
          dynamic(typedColumn.rendererRevisionProperty.map { _ =>
            val cell = typedColumn.cellFactoryProperty.get
              .fold(new TableCell[S, Any])(_(typedColumn))
            cell.bind(TableRow.this, typedColumn)
            cell
          })
        }
      )
    ) {}
  }

  private[control] def bindItem(rowIndex: Int, value: Option[S], owner: TableView[S]): Unit = {
    require(ownerTable == null && !isDisposed, "A TableRow can only be assigned to a table once")
    ownerTable = owner
    indexState.set(rowIndex)
    itemState.set(value.orNull)
    placeholder = value.isEmpty
    emptyState.set(placeholder)
    selectedState.set(
      !placeholder && !owner.selectionModel.cellSelectionEnabled &&
        owner.selectionModel.isSelected(rowIndex)
    )
  }

  private[control] def bind(
      rowIndex: Int,
      rowValue: S,
      owner: TableView[S],
      rowColumns: Seq[TableColumn[S, ?]]
  ): Unit = {
    bindItem(rowIndex, Some(rowValue), owner)
  }

  private[control] def bindPlaceholder(
      rowIndex: Int,
      owner: TableView[S],
      rowColumns: Seq[TableColumn[S, ?]]
  ): Unit = {
    bindItem(rowIndex, None, owner)
  }

  private def requireTableView(): TableView[S] =
    Option(ownerTable).getOrElse {
      throw new IllegalStateException("TableRow must be bound to a TableView before composition")
    }
}

object TableRow {
  def tableRow[S](body: TableRow[S] ?=> Cursor ?=> Unit)(using
      AbstractComponent,
      Cursor
  ): TableRow[S] =
    DslLayer.child(new TableRow[S](body)) {}

  def rowItem[S](
      rowIndex: Int,
      rowValue: S,
      tableView: TableView[S],
      columns: Seq[TableColumn[S, ?]],
      rowHeight: Double
  )(using row: TableRow[S]): Unit =
    row.bind(rowIndex, rowValue, tableView, columns)

  def placeholderRow[S](
      rowIndex: Int,
      tableView: TableView[S],
      columns: Seq[TableColumn[S, ?]],
      rowHeight: Double
  )(using row: TableRow[S]): Unit =
    row.bindPlaceholder(rowIndex, tableView, columns)
}
