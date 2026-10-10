package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel
private[bridge] object RouterRuntime {
  ComponentRegistry.register("router", RouterFactory)
  ComponentRegistry.register("router-outlet", RouterOutletFactory)
  ComponentRegistry.register("router-link", RouterLinkFactory)

  @JSExportTopLevel("installRouterRuntime", "router")
  def install(): Unit = ()
}
