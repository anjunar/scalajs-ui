package ui.bridge

import ui.core.state.{
  Disposable => CoreDisposable,
  ReadOnlyProperty => CoreReadOnlyProperty,
  WritableProperty => CoreWritableProperty
}

import scala.scalajs.js

/** The facade for a `ReadOnlyProperty<T>` TypeScript hands *into* Scala -- as `when`'s `active`, as
  * `forEach`'s `items`, as an option value resolved through [[ReactiveBridge.asProperty]]. Native,
  * because Scala never constructs one: every `ReadOnlyProperty<T>` in a TS consumer's hands
  * originated from [[UiRuntimeBridge.property]], `.map`, or a library component, and this trait
  * only has to describe its shape, not build it.
  */
@js.native
trait JsReadOnlyProperty[T] extends js.Object {
  def get: T                                                               = js.native
  def observe(observer: js.Function1[T, Unit]): JsDisposable               = js.native
  def observeWithoutInitial(observer: js.Function1[T, Unit]): JsDisposable = js.native
}
