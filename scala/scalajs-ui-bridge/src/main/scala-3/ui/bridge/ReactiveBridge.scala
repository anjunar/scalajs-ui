package ui.bridge

import ui.core.state.{Disposable as CoreDisposable, ReadOnlyProperty as CoreReadOnlyProperty, WritableProperty as CoreWritableProperty}

import scala.scalajs.js

/** Resolves `Reactive<T> = T | ReadOnlyProperty<T>` at the boundary.
  *
  * Mirrors `dsl.ts`'s own `isProperty`: an object exposing a callable `observe` is treated as a
  * property, anything else as a constant. TypeScript already applies the same duck test for `attr`,
  * `style` and `domProperty`, which resolve `Reactive` without ever crossing into Scala; this is
  * the one place the bridge has to do it itself, because `text()` and component options hand a
  * `Reactive` straight through `ScopeHandle`.
  */
object ReactiveBridge {

  def isObservable(value: js.Any): Boolean =
    value != null &&
      js.typeOf(value) == "object" &&
      js.typeOf(value.asInstanceOf[js.Dynamic].observe) == "function"

  def isWritable(value: js.Any): Boolean =
    isObservable(value) &&
      js.typeOf(value.asInstanceOf[js.Dynamic].set) == "function" &&
      js.typeOf(value.asInstanceOf[js.Dynamic].setAlways) == "function"

  def wrap[T](property: JsReadOnlyProperty[T]): CoreReadOnlyProperty[T] =
    new CoreReadOnlyProperty[T] {
      override def get: T = property.get

      override def observe(observer: T => Unit): CoreDisposable = {
        val handle = property.observe(value => observer(value))
        () => handle.dispose()
      }

      override def observeWithoutInitial(observer: T => Unit): CoreDisposable = {
        val handle = property.observeWithoutInitial(value => observer(value))
        () => handle.dispose()
      }
    }

  def wrapWritable[T](property: JsWritableProperty[T]): CoreWritableProperty[T] =
    new CoreWritableProperty[T] {
      override def get: T = property.get

      override def set(value: T): Unit = property.set(value)

      override def setAlways(value: T): Unit = property.setAlways(value)

      override def observe(observer: T => Unit): CoreDisposable = {
        val handle = property.observe(value => observer(value))
        () => handle.dispose()
      }

      override def observeWithoutInitial(observer: T => Unit): CoreDisposable = {
        val handle = property.observeWithoutInitial(value => observer(value))
        () => handle.dispose()
      }
    }

  def asProperty[T](value: js.Any): CoreReadOnlyProperty[T] =
    if (isWritable(value)) wrapWritable[T](value.asInstanceOf[JsWritableProperty[T]])
    else if (isObservable(value)) wrap[T](value.asInstanceOf[JsReadOnlyProperty[T]])
    else new ConstantProperty[T](value.asInstanceOf[T])
}
