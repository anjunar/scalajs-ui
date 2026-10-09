package ui.control.table

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.on
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.Property
import ui.core.statement.DynamicComponentRenderer.dynamic

import scala.scalajs.js
import scala.util.control.NonFatal
private final class TableTextFieldError(error: Property[Option[String]], errorId: String)
    extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-text-field-cell__error")
    setAttribute("role", "alert")
    if (errorId.nonEmpty) setAttribute("id", errorId)
    text(error.map(_.getOrElse(""))) {}
  }
}
