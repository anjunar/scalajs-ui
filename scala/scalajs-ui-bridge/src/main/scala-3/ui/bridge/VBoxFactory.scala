package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.layout.{Button, HBox, VBox}
import ui.core.render.Cursor

import scala.scalajs.js

/** The registry entries the prototype ships with. `vbox` and `hbox` live in `ui.core.layout`
  * already, so registering them costs nothing beyond the wiring below; a fourth entry is one more
  * `object` plus one line in [[BridgeRuntime]], exactly as JAVASCRIPT_API.md §4 describes.
  *
  * `scalajs-ui-controls` is deliberately not reached into here: this module depends on
  * `scalajs-ui-core` alone (JAVASCRIPT_API.md §9, step 2 -- "nur core"). Filling out the registry
  * with combo-box, table-view and friends is step 6, once the boundary itself has proven out.
  */
private[bridge] object VBoxFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    VBox.vbox {
      val self        = summon[VBox]
      val childCursor = summon[Cursor]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, childCursor))
    }
}
