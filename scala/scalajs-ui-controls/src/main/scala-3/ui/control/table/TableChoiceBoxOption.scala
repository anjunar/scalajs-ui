package ui.control.table

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.on
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.ListProperty
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.core.statement.Foreach.foreachIndexed

import scala.scalajs.js
private final class TableChoiceBoxOption[T](item: T, index: Int, converter: T => String)
    extends AbstractComponent {
  override val tagName: String = "option"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    setAttribute("value", index.toString)
    text(converter(item)) {}
  }
}
