package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.layout.Drawer
import ui.core.render.Cursor

import scala.scalajs.js
private[bridge] object DrawerContentFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    parent match {
      case drawer: Drawer =>
        given Drawer = drawer
        Drawer.drawerContent {
          val slotParent = summon[AbstractComponent]
          val slotCursor = summon[Cursor]
          body(
            new ComponentHandleBridge(slotParent),
            new ScopeHandleBridge(slotParent, slotCursor)
          )
        }
        parent
      case _ =>
        throw new IllegalStateException("drawerContent() must be composed inside drawer().")
    }
}
