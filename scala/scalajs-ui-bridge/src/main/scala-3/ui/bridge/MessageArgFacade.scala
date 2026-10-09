package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js

@js.native
private[bridge] trait MessageArgFacade extends js.Object {
  val name: String  = js.native
  val value: js.Any = js.native
}
