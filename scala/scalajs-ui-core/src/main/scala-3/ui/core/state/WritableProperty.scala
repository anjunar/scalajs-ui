package ui.core.state

/** A readable property whose current value can be replaced.
  *
  * Data sources deliberately remain read-only. This narrower contract is used when a control may
  * write back through a value property supplied by the application.
  */
trait WritableProperty[V] extends ReadOnlyProperty[V] {
  def set(value: V): Unit
  def setAlways(value: V): Unit
}
