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

/** `tabs` -- a tab strip declared from an array of `{ title, content }`. */
private[bridge] object TabsFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val tabDefs = options("tabs").asInstanceOf[js.Array[TabFacade]]

    Tabs.tabs {
      options.get("renderMode").foreach { mode =>
        Tabs.renderMode = ControlFactories.str(mode) match {
          case "keep-mounted" => Tabs.RenderMode.KeepMountedHidden
          case _              => Tabs.RenderMode.ActiveOnly
        }
      }

      // Tabs are registered before the selection is set: `setSelectedIndex` clamps against the
      // current tab count, so a selection applied to an empty strip would collapse to 0.
      tabDefs.foreach { tab =>
        Tabs.tab(ControlFactories.strProp(tab.title)) {
          tab.content(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
        }
      }

      options
        .get("selectedIndex")
        .foreach(value => Tabs.selectedIndex_=(ControlFactories.intProp(value)))
    }
  }
}
