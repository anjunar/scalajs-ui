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

@js.native
private[bridge] trait ColumnFacade extends js.Object {
  val text: String                                = js.native
  val columns: js.UndefOr[js.Array[ColumnFacade]] = js.native
  val prefWidth: js.UndefOr[Double]               = js.native
  val minWidth: js.UndefOr[Double]                = js.native
  val maxWidth: js.UndefOr[Double]                = js.native
  val resizable: js.UndefOr[js.Any]               = js.native
  val reorderable: js.UndefOr[js.Any]             = js.native
  val editable: js.UndefOr[js.Any]                = js.native
  val sortable: js.UndefOr[Boolean]               = js.native
  val sortKey: js.UndefOr[String]                 = js.native
  val headerClass: js.UndefOr[js.Any]             = js.native
  val cellClass: js.UndefOr[js.Any]               = js.native

  /** `() => void`, run through the same ambient-scope DSL as `cell`. Replaces the default header
    * text entirely (C09) -- an icon next to the label, a badge, or a `contextmenu` trigger for the
    * app's own viewport overlay all fall out of this being real composition, not a narrow property.
    */
  val headerCell: js.UndefOr[js.Function1[ScopeHandleBridge, Unit]] = js.native

  /** `(state) => void` where `state` is a live `ReadOnlyProperty<TableSortIndicatorState>`.
    * Replaces the default CSS-only sort arrow for a leaf, sortable column (C09).
    */
  val sortIndicator: js.UndefOr[
    js.Function1[ReadOnlyPropertyHandle[js.Any], js.Function1[ScopeHandleBridge, Unit]]
  ]                                                                   = js.native
  val visible: js.UndefOr[js.Any]                                     = js.native
  val onVisibilityChange: js.UndefOr[js.Function1[Boolean, Unit]]     = js.native
  val onEditStart: js.UndefOr[js.Function1[js.Object, Unit]]          = js.native
  val editCommitHandler: js.UndefOr[js.Function1[js.Object, Unit]]    = js.native
  val onEditCommit: js.UndefOr[js.Function1[js.Object, Unit]]         = js.native
  val onEditCancel: js.UndefOr[js.Function1[js.Object, Unit]]         = js.native
  val standardCell: js.UndefOr[String]                                = js.native
  val editOnBlur: js.UndefOr[String]                                  = js.native
  val standardItems: js.UndefOr[js.Any]                               = js.native
  val standardConverter: js.UndefOr[js.Function1[js.Any, String]]     = js.native
  val standardIdentityBy: js.UndefOr[js.Function1[js.Any, js.Any]]    = js.native
  val standardTextFormatter: js.UndefOr[js.Function1[js.Any, String]] = js.native
  val standardTextParser: js.UndefOr[js.Function1[String, js.Any]]    = js.native

  /** `(row, context) => (scope) => void` -- the cell body, already wrapped in `withScope` on the TS
    * side. `context` is this cell's own index/empty/selected/focused/editing state (D05).
    */
  val cell: js.UndefOr[
    js.Function2[js.Any, TableCellContextBridge, js.Function1[ScopeHandleBridge, Unit]]
  ]                                                   = js.native
  val value: js.UndefOr[js.Function1[js.Any, js.Any]] = js.native

  /** `(value, row, context) => (scope) => void`, same `context` as `cell` above (D05). */
  val valueCell: js.UndefOr[
    js.Function3[
      ReadOnlyPropertyHandle[js.Any],
      js.Any,
      TableCellContextBridge,
      js.Function1[ScopeHandleBridge, Unit]
    ]
  ] = js.native
}

