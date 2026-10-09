package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js

@js.native
private[bridge] trait RuntimeMessageFacade extends js.Object {
  val key: MessageKeyFacade            = js.native
  val args: js.Array[MessageArgFacade] = js.native
}
