package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js

@js.native
private[bridge] trait MessageKeyFacade extends js.Object {
  val source: String                                    = js.native
  val context: js.UndefOr[String]                       = js.native
  val fingerprint: String                               = js.native
  val placeholders: js.Array[String]                    = js.native
  val position: js.UndefOr[MessageSourcePositionFacade] = js.native
}
