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

/** A table-managed selection editor using the Forms ComboBox and its viewport overlay. */
final class TableComboBoxCell[S, T](
    val items: ListProperty[T],
    val converter: T => String = (value: T) => Option(value).fold("")(_.toString),
    val identityBy: T => Any = (value: T) => value.asInstanceOf[Any]
) extends TableCell[S, T] {

  override private[table] def supportsIntegratedEditor: Boolean = true

  override protected def renderContent(using AbstractComponent, Cursor): Unit =
    dynamic(
      editingProperty.map(
        if (_) new TableComboBoxEditor(this) else new TableComboBoxDisplay(this)
      )
    )
}

object TableComboBoxCell {
  def comboBoxCell[S, T](
      items: ListProperty[T],
      converter: T => String = (value: T) => Option(value).fold("")(_.toString),
      identityBy: T => Any = (value: T) => value.asInstanceOf[Any]
  )(using column: TableColumn[S, T]): Unit =
    column.cellFactoryProperty.set(Some(_ => new TableComboBoxCell(items, converter, identityBy)))
}
