package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.render.Cursor
import ui.core.state.Disposable
import ui.viewport.{Overlay, Viewport, Window}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** `viewport` -- the root host for windows, overlays and notifications. Mirrors
  * `Viewport.viewport`.
  */
private[bridge] object ViewportFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    Viewport.viewport {
      body(
        new ComponentHandleBridge(summon[AbstractComponent]),
        new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor])
      )
    }
}
