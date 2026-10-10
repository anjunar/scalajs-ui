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

/** A compact native single-selection editor backed by a live list of choices. */
final class TableChoiceBoxCell[S, T](
    val items: ListProperty[T],
    val converter: T => String = (value: T) => Option(value).fold("")(_.toString),
    val identityBy: T => Any = (value: T) => value.asInstanceOf[Any]
) extends TableCell[S, T] {

  override private[table] def supportsIntegratedEditor: Boolean = true

  private[table] def same(left: T, right: T): Boolean =
    if (left == null || right == null) left == right
    else identityBy(left) == identityBy(right)

  override protected def renderContent(using AbstractComponent, Cursor): Unit =
    dynamic(
      editingProperty.map(
        if (_) new TableChoiceBoxEditor(this) else new TableChoiceBoxDisplay(this)
      )
    )
}
