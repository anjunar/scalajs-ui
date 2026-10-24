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

private[bridge] object ComponentFactory {

  /** An optional option, read the way a native facade's own `js.UndefOr` field would be.
    *
    * `js.Dictionary.get` cannot tell "key absent" from "key present with value `undefined`" apart,
    * and both are legitimate at this boundary: TypeScript's optional `type?` is missing from the
    * object literal when omitted, but present as `undefined` when spread from a variable that
    * happens to hold `undefined` (`{ label, ...options }`). Reading through `js.UndefOr` treats
    * both as "not given", so a raw `component()` caller cannot push an `undefined` into
    * `setAttribute`, a class name or a number coercion.
    */
  def optional(options: js.Dictionary[js.Any], key: String): Option[js.Any] =
    options.getOrElse(key, js.undefined).asInstanceOf[js.UndefOr[js.Any]].toOption
}
