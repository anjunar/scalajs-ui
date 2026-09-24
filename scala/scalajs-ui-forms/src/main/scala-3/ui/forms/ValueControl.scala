package ui.forms

import ui.core.component.AbstractComponent
import ui.core.state.Property

/** A form control whose value can be bound directly to an external property. */
trait ValueControl[V] extends Control[V] { self: AbstractComponent =>
  override val valueProperty: Property[V]

  def bindValue(source: Property[V]): Unit =
    addDisposable(Property.subscribeBidirectional(source, valueProperty))
}
