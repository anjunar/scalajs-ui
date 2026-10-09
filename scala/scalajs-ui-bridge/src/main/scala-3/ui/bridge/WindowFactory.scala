package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.render.Cursor
import ui.core.state.Disposable
import ui.viewport.{Overlay, Viewport, Window}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** `window` -- a movable island in the viewport, open for as long as it is mounted. Mounts nothing
  * at the call site; see the file-level doc comment for why.
  */
private[bridge] object WindowFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val title    = ControlFactories.str(options("title"))
    val widthPx  = options.get("widthPx").map(ControlFactories.int).getOrElse(520)
    val heightPx = options.get("heightPx").map(ControlFactories.int).getOrElse(360)
    val resizable = options.get("resizable").map(ControlFactories.bool).getOrElse(true)
    val placement = options.get("placement").map(ControlFactories.str) match {
      case Some("centered") => Viewport.WindowPlacement.Centered
      case _ => Viewport.WindowPlacement.Cascaded
    }
    val mobileSheet = options.get("mobileSheet").map(ControlFactories.bool).getOrElse(true)
    val autoHeight = options.get("autoHeight").map(ControlFactories.bool).getOrElse(false)
    val onClose  = options.get("onClose").map(_.asInstanceOf[js.Function0[Unit]]).orUndefined

    val conf = Viewport.WindowConf(
      title,
      widthPx,
      heightPx,
      resizable = resizable,
      placement = placement,
      mobileSheet = mobileSheet,
      autoHeight = autoHeight,
      onClose = onClose.toOption.map(cb => (_: Window) => cb())
    ) {
      body(
        new ComponentHandleBridge(summon[AbstractComponent]),
        new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor])
      )
    }

    Viewport.addWindow(conf)(using parent)
    parent.addDisposable(Disposable(Viewport.closeWindow(conf)))
    parent
  }
}
