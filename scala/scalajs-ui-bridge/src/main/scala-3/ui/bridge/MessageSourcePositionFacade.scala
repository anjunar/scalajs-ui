package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js

/** Step 7 of JAVASCRIPT_API.md §9: the i18n facade.
  *
  * `ui.core.i18n.i18n"..."` is a Scala-3 macro (`I18nInterpolator.scala`): it derives the message
  * source, placeholder names and a fingerprint from the AST at compile time. TypeScript has no
  * macros, so `npm/scalajs-ui-core/src/i18n.ts`'s `` i18n`...` `` tag does the same derivation at
  * runtime instead -- the same source-reconstruction and the same FNV-1a fingerprint, just computed
  * from a `TemplateStringsArray` instead of a quasiquote. Placeholder names are never inferred from
  * the substituted expression (`I18nMacros.placeholderName` reads the *identifier*, which
  * TypeScript does not preserve at runtime): every substitution must be `named("x", value)`,
  * enforced by `i18n.ts` itself.
  *
  * That symmetry is what makes this file small: a `RuntimeMessage` built in TypeScript has the
  * exact same shape as one the Scala macro builds, so [[I18nFactories.toScala]] is a straight
  * field-by-field crossing, not a re-derivation. Resolution itself -- locale fallback, catalog
  * lookup, placeholder interpolation -- stays entirely in `I18nResolver`; nothing here duplicates
  * it.
  */
@js.native
private[bridge] trait MessageSourcePositionFacade extends js.Object {
  val file: String = js.native
  val line: Int    = js.native
  val column: Int  = js.native
}
