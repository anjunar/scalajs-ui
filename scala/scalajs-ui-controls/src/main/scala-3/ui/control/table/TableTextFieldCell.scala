package ui.control.table

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.on
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.statement.DynamicComponentRenderer.dynamic

import scala.scalajs.js

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
        if (_) new TableTextFieldEditor(this) else new TableTextFieldDisplay(this)
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

private final class TableTextFieldDisplay[S](cell: TableTextFieldCell[S])
    extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-text-field-cell__display")
    text(cell.itemProperty.map(value => Option(value).getOrElse(""))) {}
  }
}

private final class TableTextFieldEditor[S](cell: TableTextFieldCell[S]) extends AbstractComponent {
  override val tagName: String = "input"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-text-field-cell__editor")
    setAttribute("type", "text")
    Option(cell.tableColumn).foreach(column => setAttribute("aria-label", column.text))
    setProperty("value", currentDraft)

    on("input") { event =>
      event.raw match {
        case raw: dom.Event =>
          raw.target match {
            case input: dom.HTMLInputElement =>
              Option(cell.tableView).foreach(_.updateEdit(input.value))
            case _ => ()
          }
        case _ => ()
      }
    }
    on("keydown") { event =>
      event.raw match {
        case key: dom.KeyboardEvent if !key.defaultPrevented && !key.isComposing =>
          key.key match {
            case "Enter" =>
              key.preventDefault(); key.stopPropagation()
              Option(cell.tableView).foreach { table =>
                if (table.commitEdit(nativeValue)) table.focusGridAfterEditor()
              }
            case "Escape" =>
              key.preventDefault(); key.stopPropagation()
              Option(cell.tableView).foreach { table =>
                if (table.cancelEdit()) table.focusGridAfterEditor()
              }
            case "Tab" =>
              key.preventDefault(); key.stopPropagation()
              val row    = cell.indexProperty.get
              val column = cell.tableColumn
              Option(cell.tableView).foreach { table =>
                if (table.commitEdit(nativeValue))
                  Option(column).fold(table.focusGridAfterEditor())(
                    table.moveFocusAfterEdit(row, _, key.shiftKey)
                  )
              }
            case _ => ()
          }
        case _ => ()
      }
    }
    on("blur") { _ =>
      if (cell.editing)
        Option(cell.tableView).foreach { table =>
          cell.blurPolicy match {
            case TableTextFieldCell.BlurPolicy.Keep   => ()
            case TableTextFieldCell.BlurPolicy.Commit => table.commitEdit(nativeValue)
            case TableTextFieldCell.BlurPolicy.Cancel => table.cancelEdit()
          }
        }
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser)
      host.asInstanceOf[DomHostElement].node match {
        case input: dom.HTMLInputElement =>
          input.focus(js.Dynamic.literal(preventScroll = true).asInstanceOf[dom.FocusOptions])
          input.select()
        case _ => ()
      }

  private def currentDraft: String =
    Option(cell.tableView)
      .flatMap(table => Option(table.editingValueProperty.get))
      .fold(Option(cell.itemProperty.get).getOrElse("").toString)(_.toString)

  private def nativeValue: String =
    host
      .property[js.Any]("value")
      .filter(value => value != null && !js.isUndefined(value))
      .fold("")(_.toString)
}
