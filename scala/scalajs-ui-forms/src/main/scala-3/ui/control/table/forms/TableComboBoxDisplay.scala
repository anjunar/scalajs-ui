package ui.control.table.forms

import org.scalajs.dom
import ui.control.table.{TableCell, TableColumn}
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.on
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.ListProperty
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.forms.ComboBox
import ui.forms.ComboBox.*

import scala.scalajs.js
private final class TableComboBoxDisplay[S, T](cell: TableComboBoxCell[S, T])
    extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-combo-box-cell__display")
    text(cell.itemProperty.map {
      case null  => ""
      case value => cell.converter(value.asInstanceOf[T])
    }) {}
  }
}
