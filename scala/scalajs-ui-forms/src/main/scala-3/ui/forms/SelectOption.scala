package ui.forms

import scala.scalajs.js
import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.on
import ui.core.layout.TextComponent.text
import ui.core.layout.OptionElement
import ui.core.render.Cursor
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue
import ui.forms.Form.FormContext

/** One native select option; the label may change with the current locale. */
final case class SelectOption(value: String, label: ReadOnlyProperty[String])

object SelectOption {
  def apply[T](value: String, label: T)(using TextValue[T], AbstractComponent): SelectOption =
    new SelectOption(value, TextValue.asReadOnlyProperty(label))
}
