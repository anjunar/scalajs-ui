package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.layout.{Button, HBox, VBox}
import ui.core.render.Cursor

import scala.scalajs.js
private[bridge] object HBoxFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    HBox.hbox {
      val self        = summon[HBox]
      val childCursor = summon[Cursor]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, childCursor))
    }
}
