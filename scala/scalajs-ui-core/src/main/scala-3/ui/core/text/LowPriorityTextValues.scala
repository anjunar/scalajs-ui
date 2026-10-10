package ui.core.text

import ui.core.component.AbstractComponent
import ui.core.state.{Property, ReadOnlyProperty}

private[text] trait LowPriorityTextValues {
  given reactiveTextValue[T](using textValue: TextValue[T]): TextValue[ReadOnlyProperty[T]] with
    override def asReadOnlyProperty(value: ReadOnlyProperty[T])(using
        component: AbstractComponent
    ): ReadOnlyProperty[String] =
      value.flatMap(next => textValue.asReadOnlyProperty(next))
}
