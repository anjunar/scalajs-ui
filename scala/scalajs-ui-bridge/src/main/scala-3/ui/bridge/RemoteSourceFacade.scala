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

/** Step 6 of JAVASCRIPT_API.md §9: the controls facade.
  *
  * The trigger from CLAUDE_REVIEW_3.md §5 was "`scalajs-ui-bridge` exports the controls registry --
  * `table-view`, `data-grid`, `tabs`, `carousel`, `virtual-list-view`". This file is those five
  * factories plus the JS <-> Scala translation they need: a data source (local `ListProperty` or a
  * remote spec), item renderers, and the table column model.
  *
  * The item type is `js.Any` end to end -- a renderer or a cell hands back the exact opaque object
  * the JS consumer put into the source. Nothing about the item is interpreted on this side.
  *
  * What is *not* projected in this pass (each has a trigger in CLAUDE_REVIEW_3.md, Nachtrag Lauf
  * 4): imperative handles (`carousel.next()`, `tableView.select(item)`, `dataGrid.scrollTo`), the
  * `onRowDoubleClick` / `selectedItem` readback. Observed cell values and value-cell renderers are
  * projected below; imperative control handles remain a separate step.
  */

@js.native
private[bridge] trait RemoteSourceFacade extends js.Object {

  /** Loads one page for a query. The query shape is owned entirely by the JS consumer; this side
    * only carries it back and forth as an opaque value.
    */
  val load: js.Function1[js.Any, js.Promise[RemotePageFacade]] = js.native

  val initialQuery: js.Any = js.native

  /** The first page, already materialized. Server rendering shows exactly this slice -- there is no
    * synchronous mount point at which the bridge could await `load`.
    */
  val initial: js.UndefOr[js.Array[js.Any]] = js.native

  /** Absolute index represented by `initial[0]`; useful when SSR renders an addressable page. */
  val initialOffset: js.UndefOr[Int] = js.native

  val totalCount: js.UndefOr[Int] = js.native

  /** `(query, offset, limit) => query` -- enables range loading while scrolling. */
  val rangeQuery: js.UndefOr[js.Function3[js.Any, Int, Int, js.Any]] = js.native

  /** `(query, sorting) => query` -- enables the sortable column header. */
  val sortQuery: js.UndefOr[js.Function2[js.Any, js.Array[SortFacade], js.Any]] = js.native
}
