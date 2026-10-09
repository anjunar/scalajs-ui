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

/** `data-grid` -- fixed-size cells in a responsive column count, one renderer per cell. */
private[bridge] object DataGridFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    given ExecutionContext = ExecutionContext.global

    val src  = ControlFactories.source(options("source"))
    val cell = options("cellRenderer")
      .asInstanceOf[js.Function2[js.Any, Int, js.Function1[ScopeHandleBridge, Unit]]]

    DataGrid.dataGrid[js.Any](src) {
      DataGrid.cellRenderer = ControlFactories.itemRenderer(cell)
      options
        .get("itemWidthPx")
        .foreach(value => DataGrid.itemWidthPx = ControlFactories.dbl(value))
      options
        .get("itemHeightPx")
        .foreach(value => DataGrid.itemHeightPx = ControlFactories.dbl(value))
      options.get("gapPx").foreach(value => DataGrid.gapPx = ControlFactories.dbl(value))
      options
        .get("overscanRows")
        .foreach(value => DataGrid.overscanRows = ControlFactories.int(value))
      options
        .get("prefetchItems")
        .foreach(value => DataGrid.prefetchItems = ControlFactories.int(value))
      options.get("paging").foreach(value => DataGrid.paging = ControlFactories.bool(value))
      options.get("pageSize").foreach(value => DataGrid.pageSize = ControlFactories.int(value))
      options.get("headerRows").foreach(value => DataGrid.headerRows = ControlFactories.int(value))
      options.get("crawlable").foreach(value => DataGrid.crawlable = ControlFactories.bool(value))
      options.get("crawlId").foreach(value => DataGrid.crawlId = ControlFactories.str(value))

      options.get("toolbar").foreach { slot =>
        DataGrid.toolbar[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
      options.get("header").foreach { slot =>
        DataGrid.header[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
      options.get("loadingPlaceholder").foreach { slot =>
        DataGrid.loadingPlaceholder[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
      options.get("emptyPlaceholder").foreach { slot =>
        DataGrid.emptyPlaceholder[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
    }
  }
}
