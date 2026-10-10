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

/** `carousel` -- looping slides over a `ListProperty`, one renderer per slide. */
private[bridge] object CarouselFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val items = options("items")
      .asInstanceOf[ListPropertyHandle[js.Any]]
      .underlyingList
    val slide = options("slideRenderer")
      .asInstanceOf[js.Function2[js.Any, Int, js.Function1[ScopeHandleBridge, Unit]]]

    Carousel.carousel[js.Any] {
      val self: Carousel[js.Any] = summon[Carousel[js.Any]]
      self.setItems(items)
      self.setRenderer { (item: js.Any, index: Int) => (_: AbstractComponent) ?=> (_: Cursor) ?=>
        slide(item, index)(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
      }
      options
        .get("autoAdvanceMs")
        .foreach(value => Carousel.autoAdvanceMs_=(ControlFactories.intProp(value)))
      options
        .get("wrapAround")
        .foreach(value => Carousel.wrapAround_=(ControlFactories.boolProp(value)))
      options
        .get("sidePreviewCount")
        .foreach(value => Carousel.sidePreviewCount_=(ControlFactories.intProp(value)))
      options
        .get("ssrShowAllStates")
        .foreach(value => Carousel.ssrShowAllStates_=(ControlFactories.boolProp(value)))
      options
        .get("activeIndex")
        .foreach(value => Carousel.activeIndex_=(ControlFactories.intProp(value)))
    }
  }
}
