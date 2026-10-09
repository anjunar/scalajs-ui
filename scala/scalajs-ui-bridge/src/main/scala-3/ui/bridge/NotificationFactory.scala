package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.render.Cursor
import ui.core.state.Disposable
import ui.viewport.{Overlay, Viewport, Window}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** `notification` -- short feedback that dismisses itself. Mirrors `Viewport.notify`. Mounts
  * nothing at the call site; see the file-level doc comment for why.
  */
private[bridge] object NotificationFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val message = ControlFactories.str(options("message"))
    val kind    = options
      .get("kind")
      .map(ControlFactories.str)
      .map(ViewportFactories.notificationKind)
      .getOrElse(Viewport.NotificationKind.Info)
    val durationMs = options.get("durationMs").map(ControlFactories.int).getOrElse(3000)

    val conf = Viewport.notify(message, kind, durationMs)(using parent)
    parent.addDisposable(Disposable(Viewport.closeNotification(conf)))
    parent
  }
}
