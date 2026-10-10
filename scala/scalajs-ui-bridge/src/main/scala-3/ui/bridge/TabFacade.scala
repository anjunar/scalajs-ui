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

@js.native
private[bridge] trait TabFacade extends js.Object {
  val title: js.Any                                  = js.native
  val content: js.Function1[ScopeHandleBridge, Unit] = js.native
}
