package ui.bridge

import ui.control.carousel.Carousel
import ui.control.datagrid.DataGrid
import ui.control.table.{ColumnResizeRequest, CustomColumnResizePolicy, TableCheckBoxCell, TableCell, TableChoiceBoxCell, TableConvertingTextFieldCell, TableColumn, TableEditCancelEvent, TableEditCancelReason, TableEditCommitEvent, TableEditStartEvent, TableRow, TableProgressBarCell, TableTextFieldCell, TableView}
import ui.control.table.forms.TableComboBoxCell
import ui.control.tabs.Tabs
import ui.control.virtuallist.VirtualListView
import ui.core.component.AbstractComponent
import ui.core.remote.{RemoteListProperty, RemoteLoader, RemotePage, RemoteSort}
import ui.core.render.Cursor
import ui.core.state.{ListDataSource, ListProperty as CoreListProperty, ReadOnlyProperty as CoreReadOnlyProperty}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** `virtual-list-view` -- measured row heights, one renderer per row. */
private[bridge] object VirtualListFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    given ExecutionContext = ExecutionContext.global

    val src  = ControlFactories.source(options("source"))
    val cell = options("cellRenderer")
      .asInstanceOf[js.Function2[js.Any, Int, js.Function1[ScopeHandleBridge, Unit]]]

    VirtualListView.virtualList[js.Any](src) {
      VirtualListView.cellRenderer = ControlFactories.itemRenderer(cell)
      options
        .get("estimateHeightPx")
        .foreach(value => VirtualListView.estimateHeightPx = ControlFactories.dbl(value))
      options
        .get("overscanPx")
        .foreach(value => VirtualListView.overscanPx = ControlFactories.dbl(value))
      options
        .get("prefetchItems")
        .foreach(value => VirtualListView.prefetchItems = ControlFactories.int(value))
      options.get("paging").foreach(value => VirtualListView.paging = ControlFactories.bool(value))
      options
        .get("pageSize")
        .foreach(value => VirtualListView.pageSize = ControlFactories.int(value))
      options
        .get("headerRows")
        .foreach(value => VirtualListView.headerRows = ControlFactories.int(value))
      options
        .get("crawlable")
        .foreach(value => VirtualListView.crawlable = ControlFactories.bool(value))
      options.get("crawlId").foreach(value => VirtualListView.crawlId = ControlFactories.str(value))

      options.get("header").foreach { slot =>
        VirtualListView.header[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
    }
  }
}
