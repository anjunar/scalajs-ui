package ui.bridge

import ui.core.state.{Disposable => CoreDisposable}

import scala.scalajs.js

/** The facade for a `Disposable` TypeScript hands *back* to Scala -- today only as the return value
  * of a `JsReadOnlyProperty`'s own `observe`. Native, because Scala never constructs one.
  */
@js.native
trait JsDisposable extends js.Object {
  def dispose(): Unit = js.native
}
