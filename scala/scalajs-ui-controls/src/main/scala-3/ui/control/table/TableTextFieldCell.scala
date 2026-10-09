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

/** A table-managed String editor. The display component is replaced by a native text input only for
  * the active edit session.
  */
final class TableTextFieldCell[S](
    val blurPolicy: TableTextFieldCell.BlurPolicy = TableTextFieldCell.BlurPolicy.Keep
) extends TableCell[S, String] {

  override private[table] def supportsIntegratedEditor: Boolean = true

  override protected def renderContent(using AbstractComponent, Cursor): Unit =
    dynamic(
      editingProperty.map(
        if (_)
          new TableTextFieldEditor[S, String](
            this,
            value => value,
            value => Right(value),
            blurPolicy
          )
        else new TableTextFieldDisplay[S, String](this, value => value)
      )
    )
}

object TableTextFieldCell {
  enum BlurPolicy {

    /** Moving DOM focus elsewhere leaves the edit session open. */
    case Keep

    /** A blur commits the current input when the value can be written. */
    case Commit

    /** A blur cancels the current edit session. */
    case Cancel
  }
}
