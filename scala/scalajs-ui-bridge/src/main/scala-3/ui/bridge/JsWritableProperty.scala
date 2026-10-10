package ui.bridge

import ui.core.state.{Disposable as CoreDisposable, ReadOnlyProperty as CoreReadOnlyProperty, WritableProperty as CoreWritableProperty}

import scala.scalajs.js

@js.native
trait JsWritableProperty[T] extends JsReadOnlyProperty[T] {
  def set(value: T): Unit       = js.native
  def setAlways(value: T): Unit = js.native
}
