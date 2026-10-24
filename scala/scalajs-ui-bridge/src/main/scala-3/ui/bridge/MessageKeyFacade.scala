package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js

/** `contract.ts`'s `MessageContext` -- `i18n.ts` builds it as `{ value: context }`, an object, not
  * the bare string. Reading it as a string would wrap the whole object in `MessageContext(_)`, and
  * since `MessageCatalog.entryFor` compares contexts by equality, two distinct `{ value: "fruit" }`
  * objects would never match -- no contextual (`i18nc`) message could translate through the bridge.
  */
@js.native
private[bridge] trait MessageContextFacade extends js.Object {
  val value: String = js.native
}

@js.native
private[bridge] trait MessageKeyFacade extends js.Object {
  val source: String                                    = js.native
  val context: js.UndefOr[MessageContextFacade]         = js.native
  val fingerprint: String                               = js.native
  val placeholders: js.Array[String]                    = js.native
  val position: js.UndefOr[MessageSourcePositionFacade] = js.native
}
