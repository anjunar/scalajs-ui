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
private[bridge] object ControlFactories {

  private[bridge] def standardItems(column: ColumnFacade): CoreListProperty[js.Any] =
    column.standardItems.fold(CoreListProperty[js.Any]()) {
      case handle: ListPropertyHandle[?] =>
        handle.underlyingList.asInstanceOf[CoreListProperty[js.Any]]
      case values: js.Array[?] =>
        CoreListProperty(values.asInstanceOf[js.Array[js.Any]])
      case _ => CoreListProperty[js.Any]()
    }

  private[bridge] def textParseResult(result: js.Any): Either[String, js.Any] =
    if (result == null || js.isUndefined(result)) Left("Parser returned no result")
    else {
      val dynamic = result.asInstanceOf[js.Dynamic]
      val ok      = dynamic.selectDynamic("ok")
      if (js.typeOf(ok) != "boolean") Left("Parser result requires a Boolean 'ok' field")
      else if (ok.asInstanceOf[Boolean]) {
        val value = dynamic.selectDynamic("value")
        if (js.isUndefined(value)) Left("Successful parser result requires a 'value' field")
        else Right(value.asInstanceOf[js.Any])
      } else {
        val error = dynamic.selectDynamic("error")
        if (js.typeOf(error) == "string" && error.asInstanceOf[String].nonEmpty)
          Left(error.asInstanceOf[String])
        else Left("Invalid value")
      }
    }

  /** A local `ListProperty` (already a `ListDataSource`) or a remote spec. */
  def source(value: js.Any)(using ExecutionContext): ListDataSource[js.Any] =
    value match {
      case handle: ListPropertyHandle[?] =>
        handle.underlyingList.asInstanceOf[CoreListProperty[js.Any]]
      case _ =>
        remoteSource(value.asInstanceOf[RemoteSourceFacade])
    }

  private def remoteSource(
      facade: RemoteSourceFacade
  )(using ExecutionContext): RemoteListProperty[js.Any, js.Any] = {
    val loader = RemoteLoader[js.Any, js.Any] { query =>
      facade.load(query).toFuture.map { page =>
        RemotePage[js.Any, js.Any](
          items = page.items.toSeq,
          offset = page.offset.toOption,
          nextQuery = page.nextQuery.toOption,
          totalCount = page.totalCount.toOption,
          hasMore = page.hasMore.toOption
        )
      }
    }

    val remote = new RemoteListProperty[js.Any, js.Any](
      loader = loader,
      initialQuery = facade.initialQuery,
      underlying = facade.initial.getOrElse(js.Array[js.Any]()),
      initialOffset = facade.initialOffset.toOption.getOrElse(0),
      sortUpdater =
        facade.sortQuery.toOption.map { fn => (query: js.Any, sorting: Seq[RemoteSort]) =>
          fn(query, sorting.map(sortFacade).toJSArray)
        },
      rangeQueryUpdater =
        facade.rangeQuery.toOption.map { fn => (query: js.Any, offset: Int, limit: Int) =>
          fn(query, offset, limit)
        }
    )

    facade.totalCount.toOption.foreach(count => remote.totalCountProperty.set(Some(count)))
    remote
  }

  private def sortFacade(sort: RemoteSort): SortFacade =
    js.Dynamic.literal(field = sort.field, ascending = sort.ascending).asInstanceOf[SortFacade]

  /** A `(scope) => void` from TS, run against a fresh handle built from the ambient context.
    * Mirrors `RouterFactories.routeComponent`.
    */
  def slotBody(
      fn: js.Function1[ScopeHandleBridge, Unit]
  ): AbstractComponent ?=> Cursor ?=> Unit =
    (_: AbstractComponent) ?=>
      (_: Cursor) ?=> fn(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))

  /** A `(item, index) => (scope) => void` from TS, as a control cell renderer. */
  def itemRenderer(
      fn: js.Function2[js.Any, Int, js.Function1[ScopeHandleBridge, Unit]]
  ): (js.Any | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit =
    (item, index) =>
      (_: AbstractComponent) ?=>
        (_: Cursor) ?=>
          fn(item.asInstanceOf[js.Any], index)(
            new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor])
          )

  // --- option readers -------------------------------------------------------

  private[bridge] def dbl(value: js.Any): Double   = value.asInstanceOf[Double]
  private[bridge] def int(value: js.Any): Int      = value.asInstanceOf[Double].toInt
  private[bridge] def bool(value: js.Any): Boolean = value.asInstanceOf[Boolean]
  private[bridge] def str(value: js.Any): String   = value.asInstanceOf[String]

  /** Constant or reactive, always resolved to a property -- a constant becomes a `ConstantProperty`
    * that the control observes once.
    */
  private[bridge] def intProp(value: js.Any): CoreReadOnlyProperty[Int] =
    ReactiveBridge.asProperty[Double](value).map(_.toInt)

  private[bridge] def boolProp(value: js.Any): CoreReadOnlyProperty[Boolean] =
    ReactiveBridge.asProperty[Boolean](value)

  private[bridge] def strProp(value: js.Any): CoreReadOnlyProperty[String] =
    ReactiveBridge.asProperty[String](value)
}
