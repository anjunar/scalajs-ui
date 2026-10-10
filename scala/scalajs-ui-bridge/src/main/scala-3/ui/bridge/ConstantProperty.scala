package ui.bridge

import ui.core.state.{Disposable as CoreDisposable, ReadOnlyProperty as CoreReadOnlyProperty, WritableProperty as CoreWritableProperty}

import scala.scalajs.js

/** A constant lifted into `ReadOnlyProperty[T]`, for the non-reactive half of `Reactive<T>`. */
final class ConstantProperty[T](value: T) extends CoreReadOnlyProperty[T] {
  override def get: T = value

  override def observe(observer: T => Unit): CoreDisposable = {
    observer(value)
    CoreDisposable.empty
  }

  override def observeWithoutInitial(observer: T => Unit): CoreDisposable =
    CoreDisposable.empty
}
