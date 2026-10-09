package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel
private[bridge] object ControlsRuntime {
  ComponentRegistry.register("tabs", TabsFactory)
  ComponentRegistry.register("carousel", CarouselFactory)
  ComponentRegistry.register("table-view", TableViewFactory)
  ComponentRegistry.register("data-grid", DataGridFactory)
  ComponentRegistry.register("virtual-list-view", VirtualListFactory)

  @JSExportTopLevel("installControlsRuntime", "controls")
  def install(): Unit = ()
}

