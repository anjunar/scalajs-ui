package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.layout.Drawer
import ui.core.render.Cursor

import scala.scalajs.js

/** TypeScript's imperative handle for the Drawer enclosing its two slots. */
@js.native
private[bridge] trait DrawerHandleFacade extends js.Object {
  def isOpen(): Boolean             = js.native
  def setOpen(value: Boolean): Unit = js.native
  def toggle(): Unit                = js.native
}
