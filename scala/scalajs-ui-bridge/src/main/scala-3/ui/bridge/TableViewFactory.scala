package ui.bridge

import ui.control.carousel.Carousel
import ui.control.datagrid.DataGrid
import ui.control.table.{
  ColumnResizeRequest,
  CustomColumnResizePolicy,
  TableCheckBoxCell,
  TableCell,
  TableChoiceBoxCell,
  TableConvertingTextFieldCell,
  TableColumn,
  TableEditCancelEvent,
  TableEditCancelReason,
  TableEditCommitEvent,
  TableEditStartEvent,
  TableRow,
  TableProgressBarCell,
  TableTextFieldCell,
  TableView
}
import ui.control.table.forms.TableComboBoxCell
import ui.control.tabs.Tabs
import ui.control.virtuallist.VirtualListView
import ui.core.component.AbstractComponent
import ui.core.remote.{RemoteListProperty, RemoteLoader, RemotePage, RemoteSort}
import ui.core.render.Cursor
import ui.core.state.{
  ListDataSource,
  ListProperty => CoreListProperty,
  ReadOnlyProperty => CoreReadOnlyProperty
}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** `table-view` -- fixed-height rows, a column model, an optional scrolling content header. */
private[bridge] object TableViewFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    given ExecutionContext = ExecutionContext.global

    val src     = ControlFactories.source(options("source"))
    val columns = options("columns").asInstanceOf[js.Array[ColumnFacade]]

    def positionFacade(row: Int, column: Int): js.Object =
      js.Dynamic.literal(row = row, column = column)

    def editStartFacade(event: TableEditStartEvent[js.Any, js.Any]): js.Object =
      js.Dynamic.literal(
        position = positionFacade(event.position.row, event.position.column),
        rowItem = event.rowValue,
        oldValue = event.oldValue
      )

    def editCommitFacade(event: TableEditCommitEvent[js.Any, js.Any]): js.Object =
      js.Dynamic.literal(
        position = positionFacade(event.position.row, event.position.column),
        rowItem = event.rowValue,
        oldValue = event.oldValue,
        newValue = event.newValue
      )

    def editCancelFacade(event: TableEditCancelEvent[js.Any, js.Any]): js.Object =
      js.Dynamic.literal(
        position = positionFacade(event.position.row, event.position.column),
        rowItem = event.rowValue,
        oldValue = event.oldValue,
        draftValue = event.draftValue,
        reason = (event.reason match {
          case TableEditCancelReason.Explicit          => "explicit"
          case TableEditCancelReason.Replaced          => "replaced"
          case TableEditCancelReason.TableDisabled     => "table-disabled"
          case TableEditCancelReason.ColumnDisabled    => "column-disabled"
          case TableEditCancelReason.RowRemoved        => "row-removed"
          case TableEditCancelReason.RowReplaced       => "row-replaced"
          case TableEditCancelReason.SourceReset       => "source-reset"
          case TableEditCancelReason.ColumnUnavailable => "column-unavailable"
          case TableEditCancelReason.CellUnavailable   => "cell-unavailable"
          case TableEditCancelReason.Disposed          => "disposed"
        })
      )

    val table = TableView.tableView[js.Any](src) {
      options.get("rowHeight").foreach(value => TableView.rowHeight = ControlFactories.dbl(value))
      options.get("variableRowHeight").foreach { value =>
        val table = summon[TableView[js.Any]]
        table.addDisposable(
          ReactiveBridge.asProperty[Boolean](value).observe(table.variableRowHeightProperty.set)
        )
      }
      options.get("direction").foreach { value =>
        val table = summon[TableView[js.Any]]
        table.addDisposable(
          ReactiveBridge.asProperty[String](value).observe { direction =>
            table.directionProperty.set(TableViewHandleBridge.parseDirection(direction))
          }
        )
      }
      options.get("columnResizePolicy").foreach { value =>
        val table = summon[TableView[js.Any]]
        table.addDisposable(ReactiveBridge.asProperty[String](value).observe { policy =>
          table.columnResizePolicyProperty.set(TableViewHandleBridge.parseResizePolicy(policy))
        })
      }
      // C05: an escape hatch alongside the seven built-in strategies, not reactive -- set once,
      // like `row`/`rowKey` above, rather than wrapped in a Property.
      options.get("customResizePolicy").foreach { callback =>
        val table = summon[TableView[js.Any]]
        val fn    = callback.asInstanceOf[js.Function1[js.Any, js.Array[Double]]]
        val policy: CustomColumnResizePolicy = request => {
          val jsColumns = request.columns.map(c =>
            js.Dynamic.literal(
              min = c.min,
              max = c.max,
              preferred = c.preferred,
              resizable = c.resizable
            )
          )
          val jsRequest = js.Dynamic.literal(
            columns = jsColumns.toJSArray,
            widths = request.widths.toJSArray,
            viewport = request.viewport,
            targetIndices = request.target
              .map((indices, _) => indices.map(_.toDouble).toJSArray)
              .orUndefined,
            targetDelta = request.target.map((_, delta) => delta).orUndefined
          )
          fn(jsRequest).toVector
        }
        table.customResizePolicyProperty.set(Some(policy))
      }
      options.get("selectionMode").foreach { value =>
        val table  = summon[TableView[js.Any]]
        val source = ReactiveBridge.asProperty[String](value)
        table.addDisposable(source.observe { mode =>
          table.selectionModel.selectionMode = TableViewHandleBridge.parseSelectionMode(mode)
        })
        table.addDisposable(
          table.selectionModelProperty.observeWithoutInitial { model =>
            model.selectionMode = TableViewHandleBridge.parseSelectionMode(source.get)
          }
        )
      }
      options.get("cellSelectionEnabled").foreach { value =>
        val table  = summon[TableView[js.Any]]
        val source = ReactiveBridge.asProperty[Boolean](value)
        table.addDisposable(
          source.observe(table.selectionModel.cellSelectionEnabled_=)
        )
        table.addDisposable(
          table.selectionModelProperty.observeWithoutInitial { model =>
            model.cellSelectionEnabled = source.get
          }
        )
      }
      options.get("editable").foreach { value =>
        val table = summon[TableView[js.Any]]
        table.addDisposable(
          ReactiveBridge.asProperty[Boolean](value).observe(table.editableProperty.set)
        )
      }
      options
        .get("showHeader")
        .foreach(value => TableView.showHeader = ControlFactories.bool(value))
      options
        .get("tableMenuButtonVisible")
        .foreach(value =>
          TableView.tableMenuButtonVisible = ReactiveBridge.asProperty[Boolean](value)
        )
      options.get("columnMenuText").foreach { value =>
        val table = summon[TableView[js.Any]]
        table.addDisposable(
          ReactiveBridge.asProperty[String](value).observe(table.columnMenuTextProperty.set)
        )
      }
      options
        .get("showFooter")
        .foreach(value => TableView.showFooter = ControlFactories.bool(value))
      options.get("paging").foreach(value => TableView.paging = ControlFactories.bool(value))
      options.get("pageSize").foreach(value => TableView.pageSize = ControlFactories.int(value))
      options.get("headerRows").foreach(value => TableView.headerRows = ControlFactories.int(value))
      options.get("crawlable").foreach(value => TableView.crawlable = ControlFactories.bool(value))
      options.get("crawlId").foreach(value => TableView.crawlId = ControlFactories.str(value))

      options.get("row").foreach { callback =>
        val renderer = callback.asInstanceOf[js.Function4[
          TableRowContextBridge,
          ComponentHandleBridge,
          ScopeHandleBridge,
          js.Function1[ScopeHandleBridge, Unit],
          Unit
        ]]
        TableView.rowFactory_=[js.Any](_ =>
          new TableRow[js.Any] {
            override protected def renderContent(using
                parent: AbstractComponent,
                cursor: Cursor
            ): Unit = {
              var active                                       = true
              val cells: js.Function1[ScopeHandleBridge, Unit] = scope => {
                require(active, "renderCells must be called synchronously inside the row renderer")
                renderCells(using scope.parent, scope.cursor)
              }
              try
                renderer(
                  new TableRowContextBridge(this),
                  new ComponentHandleBridge(this),
                  new ScopeHandleBridge(parent, cursor),
                  cells
                )
              finally active = false
            }
          }
        )
      }
      options.get("rowKey").foreach { callback =>
        val key = callback.asInstanceOf[js.Function1[js.Any, js.Any]]
        TableView.rowKey_=[js.Any](item => key(item))
      }
      options.get("rowDisabled").foreach { callback =>
        val predicate = callback.asInstanceOf[js.Function1[js.Any, Boolean]]
        TableView.rowDisabled_=[js.Any](item => predicate(item))
      }
      options.get("onScrollTo").foreach { callback =>
        val handler = callback.asInstanceOf[js.Function1[Int, Unit]]
        TableView.onScrollTo[js.Any](index => handler(index))
      }
      options.get("onScrollToColumn").foreach { callback =>
        val handler = callback.asInstanceOf[js.Function1[Int, Unit]]
        val table   = summon[TableView[js.Any]]
        TableView.onScrollToColumn[js.Any](column => handler(table.getVisibleLeafIndex(column)))
      }

      def createColumn(col: ColumnFacade): TableColumn[js.Any, js.Any] = {
        val column = new TableColumn[js.Any, js.Any](col.text)
        col.visible.foreach(value =>
          column.addDisposable(
            ReactiveBridge.asProperty[Boolean](value).observe(column.visibleProperty.set)
          )
        )
        col.onVisibilityChange.foreach { callback =>
          column.addDisposable(
            column.visibleProperty.observeWithoutInitial(value => callback(value))
          )
        }
        col.prefWidth.foreach(column.prefWidth = _)
        col.minWidth.foreach(column.minWidth = _)
        col.maxWidth.foreach(column.maxWidth = _)
        col.resizable.foreach { value =>
          column.addDisposable(
            ReactiveBridge.asProperty[Boolean](value).observe(column.resizableProperty.set)
          )
        }
        col.reorderable.foreach { value =>
          column.addDisposable(
            ReactiveBridge.asProperty[Boolean](value).observe(column.reorderableProperty.set)
          )
        }
        col.editable.foreach { value =>
          column.addDisposable(
            ReactiveBridge.asProperty[Boolean](value).observe(column.editableProperty.set)
          )
        }
        col.sortable.foreach(column.sortableProperty.set)
        col.sortKey.foreach(key => column.sortKeyProperty.set(Some(key)))
        col.headerClass.foreach { value =>
          column.addDisposable(
            ReactiveBridge
              .asProperty[js.Array[String]](value)
              .map(_.toSeq)
              .observe(column.headerClassesProperty.set)
          )
        }
        col.cellClass.foreach { value =>
          column.addDisposable(
            ReactiveBridge
              .asProperty[js.Array[String]](value)
              .map(_.toSeq)
              .observe(column.cellClassesProperty.set)
          )
        }
        col.headerCell.foreach(renderer => column.headerCell(ControlFactories.slotBody(renderer)))
        col.sortIndicator.foreach { renderer =>
          column.sortIndicator { stateProperty =>
            val jsState = stateProperty.map { state =>
              js.Dynamic
                .literal(
                  sorted = state.sorted,
                  ascending = state.ascending,
                  priority = state.priority
                )
                .asInstanceOf[js.Any]
            }
            ControlFactories.slotBody(renderer(new ReadOnlyPropertyHandle(jsState)))
          }
        }
        col.onEditStart.foreach(callback =>
          column.onEditStartProperty.set(Some(event => callback(editStartFacade(event))))
        )
        col.editCommitHandler.foreach(callback =>
          column.editCommitHandlerProperty.set(Some(event => callback(editCommitFacade(event))))
        )
        col.onEditCommit.foreach(callback =>
          column.onEditCommitProperty.set(Some(event => callback(editCommitFacade(event))))
        )
        col.onEditCancel.foreach(callback =>
          column.onEditCancelProperty.set(Some(event => callback(editCancelFacade(event))))
        )
        col.value.foreach { accessor =>
          column.cellValueFactoryProperty.set(
            Some(features => ReactiveBridge.asProperty[js.Any](accessor(features.value)))
          )
        }
        col.cell.foreach { renderer =>
          column.setCellRenderer((row: js.Any) =>
            (cell: AbstractComponent) ?=>
              (cursor: Cursor) ?=>
                renderer(
                  row,
                  new TableCellContextBridge(cell.asInstanceOf[TableCell[js.Any, js.Any]])
                )(
                  new ScopeHandleBridge(cell, cursor)
                )
          )
        }
        col.valueCell.foreach { renderer =>
          column.cellFactoryProperty.set(
            Some(_ =>
              new TableCell[js.Any, js.Any] {
                override protected def renderContent(using
                    parent: AbstractComponent,
                    cursor: Cursor
                ): Unit =
                  renderer(
                    new ReadOnlyPropertyHandle(itemProperty),
                    tableRow.asInstanceOf[ui.control.table.TableRow[js.Any]].itemProperty.get,
                    new TableCellContextBridge(this)
                  )(new ScopeHandleBridge(parent, cursor))
              }
            )
          )
        }
        col.standardCell.foreach {
          case "text-field" =>
            val blurPolicy = col.editOnBlur.fold(TableTextFieldCell.BlurPolicy.Keep) {
              case "commit" => TableTextFieldCell.BlurPolicy.Commit
              case "cancel" => TableTextFieldCell.BlurPolicy.Cancel
              case _        => TableTextFieldCell.BlurPolicy.Keep
            }
            column.cellFactoryProperty.set(
              Some(_ =>
                new TableTextFieldCell[js.Any](blurPolicy).asInstanceOf[TableCell[js.Any, js.Any]]
              )
            )
          case "converting-text-field" =>
            val blurPolicy = col.editOnBlur.fold(TableTextFieldCell.BlurPolicy.Keep) {
              case "commit" => TableTextFieldCell.BlurPolicy.Commit
              case "cancel" => TableTextFieldCell.BlurPolicy.Cancel
              case _        => TableTextFieldCell.BlurPolicy.Keep
            }
            val formatter = col.standardTextFormatter.fold((value: js.Any) =>
              if (value == null) "" else value.toString
            )(function => value => function(value))
            val parser = col.standardTextParser.fold((_: String) =>
              Left("No parser configured"): Either[String, js.Any]
            )(function => value => ControlFactories.textParseResult(function(value)))
            column.cellFactoryProperty.set(
              Some(_ => new TableConvertingTextFieldCell(parser, formatter, blurPolicy))
            )
          case "check-box" =>
            column.cellFactoryProperty.set(
              Some(_ => new TableCheckBoxCell[js.Any].asInstanceOf[TableCell[js.Any, js.Any]])
            )
          case "choice-box" =>
            val items     = ControlFactories.standardItems(col)
            val converter = col.standardConverter.fold((value: js.Any) =>
              if (value == null) "" else value.toString
            )(function => value => function(value))
            val identity = col.standardIdentityBy.fold((value: js.Any) => value)(function =>
              value => function(value)
            )
            column.cellFactoryProperty.set(
              Some(_ => new TableChoiceBoxCell(items, converter, identity))
            )
          case "combo-box" =>
            val items     = ControlFactories.standardItems(col)
            val converter = col.standardConverter.fold((value: js.Any) =>
              if (value == null) "" else value.toString
            )(function => value => function(value))
            val identity = col.standardIdentityBy.fold((value: js.Any) => value)(function =>
              value => function(value)
            )
            column.cellFactoryProperty.set(
              Some(_ => new TableComboBoxCell(items, converter, identity))
            )
          case "progress-bar" =>
            column.editable = false
            column.cellFactoryProperty.set(
              Some(_ => new TableProgressBarCell[js.Any].asInstanceOf[TableCell[js.Any, js.Any]])
            )
          case _ => ()
        }
        col.columns.foreach(children => column.columns.setAll(children.map(createColumn).toSeq))
        column
      }
      columns.foreach(col => summon[TableView[js.Any]].columns.addOne(createColumn(col)))

      options.get("header").foreach { slot =>
        TableView.header[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
      options.get("placeholder").foreach { slot =>
        TableView.placeholder[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
    }
    options.get("receiveHandle").foreach { callback =>
      callback.asInstanceOf[js.Function1[TableViewHandleBridge, Unit]](
        new TableViewHandleBridge(table)
      )
    }
    table
  }
}
