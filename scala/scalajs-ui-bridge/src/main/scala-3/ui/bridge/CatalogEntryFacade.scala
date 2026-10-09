package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js

/** `i18n.ts`'s `CatalogEntry`: a message key plus one translation string per locale code. Native,
  * for the same reason as [[RouteFacade]] -- Scala never constructs one, only reads what
  * `i18nProvider()` handed across.
  */
@js.native
private[bridge] trait CatalogEntryFacade extends js.Object {
  val key: MessageKeyFacade               = js.native
  val translations: js.Dictionary[String] = js.native
}

