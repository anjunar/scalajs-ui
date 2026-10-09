package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.render.Cursor
import ui.core.state.Disposable
import ui.viewport.{Overlay, Viewport, Window}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** `overlay` -- an anchor-following surface, present in the tree for as long as it should stay
  * open. Mirrors `Overlay.overlay`. Always needs a reactive gate above it (`when(...)`), the same
  * as `ui.forms.ComboBox`'s own dropdown -- it mounts a real, visible `div` at the call site, so
  * (unlike `window`/`notification` below) it needs the call site's cursor to be genuinely live.
  */
private[bridge] object OverlayFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val widthPx = options.get("widthPx").map(ControlFactories.dbl)

    Overlay.overlay(widthPx) {
      val self: Overlay = summon[Overlay]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}
