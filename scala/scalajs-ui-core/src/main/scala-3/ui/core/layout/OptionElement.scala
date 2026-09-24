package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class OptionElement extends AbstractComponent {
  val tagName = "option"

  def value_=(next: String): Unit = setAttribute("value", next)

  def selected_=(next: Boolean): Unit =
    if (next) setAttribute("selected", "selected")
    else removeAttribute("selected")
}

object OptionElement {
  def option(value: String, selected: Boolean = false)(
      body: OptionElement ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): OptionElement = {
    val current = new OptionElement()
    DslLayer.child(current) {
      current.value_=(value)
      current.selected_=(selected)
      body
    }
  }
}
