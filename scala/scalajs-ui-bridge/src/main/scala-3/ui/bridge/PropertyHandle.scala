package ui.bridge

import ui.core.state.{Property => CoreProperty, ReadOnlyProperty => CoreReadOnlyProperty}

import scala.scalajs.js

/** The JS projection of `ui.core.state.Property`. Mirrors `contract.ts`'s `Property<T>`. */
final class PropertyHandle[T](private[bridge] final val underlyingProperty: CoreProperty[T])
    extends ReadOnlyPropertyHandle[T](underlyingProperty) {

  def set(value: T): Unit = underlyingProperty.set(value)

  def setAlways(value: T): Unit = underlyingProperty.setAlways(value)

  def reset(): Unit = underlyingProperty.reset()

  def isDirty: Boolean = underlyingProperty.isDirty
}
