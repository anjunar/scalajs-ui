package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.render.Cursor

import scala.scalajs.js

/** Mounts one library component by name.
  *
  * `options` is whatever `ScopeHandle.component`'s caller passed -- a raw `Record<string, unknown>`
  * from TypeScript, unchecked until a factory reads a key out of it. `body` is the JS closure to
  * run once the component and its content cursor exist, exactly like [[ScopeHandleBridge.child]]'s.
  */
private[bridge] trait ComponentFactory {
  def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent
}
