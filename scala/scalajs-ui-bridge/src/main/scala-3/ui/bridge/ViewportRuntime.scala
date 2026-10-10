package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel
private[bridge] object ViewportRuntime {
  ComponentRegistry.register("viewport", ViewportFactory)
  ComponentRegistry.register("window", WindowFactory)
  ComponentRegistry.register("overlay", OverlayFactory)
  ComponentRegistry.register("notification", NotificationFactory)

  @JSExportTopLevel("installViewportRuntime", "viewport")
  def install(): Unit = ()
}
