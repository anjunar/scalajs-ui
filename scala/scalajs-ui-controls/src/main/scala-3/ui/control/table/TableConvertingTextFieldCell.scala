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

/** A typed text editor whose parser may reject conversion or domain validation. */
final class TableConvertingTextFieldCell[S, T](
    val parser: String => Either[String, T],
    val formatter: T => String = (value: T) => Option(value).fold("")(_.toString),
    val blurPolicy: TableTextFieldCell.BlurPolicy = TableTextFieldCell.BlurPolicy.Keep
) extends TableCell[S, T] {

  override private[table] def supportsIntegratedEditor: Boolean = true

  override protected def renderContent(using AbstractComponent, Cursor): Unit =
    dynamic(
      editingProperty.map(
        if (_) new TableTextFieldEditor(this, formatter, parser, blurPolicy)
        else new TableTextFieldDisplay(this, formatter)
      )
    )
}

