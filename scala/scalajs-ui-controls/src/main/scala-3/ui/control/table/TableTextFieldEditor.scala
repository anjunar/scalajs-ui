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
private final class TableTextFieldEditor[S, T](
    val cell: TableCell[S, T],
    formatter: T => String,
    parser: String => Either[String, T],
    blurPolicy: TableTextFieldCell.BlurPolicy
) extends AbstractComponent {
  override val tagName: String = "div"

  private[table] val errorProperty = Property(Option.empty[String])
  private[table] var errorId       = ""

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-text-field-cell__editor-host")
    if (cursor.isBrowser) errorId = TableTextFieldEditor.nextErrorId()

    DslLayer.child(new TableTextFieldInput(this, initialText)) {}
    DslLayer.child(new TableTextFieldError(errorProperty, errorId)) {}

    addDisposable(errorProperty.observe {
      case Some(message) =>
        cell.addClass("ui-table-cell-edit-error")
        setAttribute("title", message)
      case None =>
        cell.removeClass("ui-table-cell-edit-error")
        removeAttribute("title")
    })
    addDisposable(() => cell.removeClass("ui-table-cell-edit-error"))
  }

  private[table] def inputChanged(value: String): Unit =
    parsed(value) match {
      case Right(converted) =>
        Option(cell.tableView).foreach(_.updateEdit(converted))
        if (errorProperty.get.nonEmpty) errorProperty.set(None)
      case Left(message) if errorProperty.get.nonEmpty => errorProperty.set(Some(message))
      case Left(_)                                     => ()
    }

  private[table] def commit(value: String, backwards: Option[Boolean]): Unit =
    parsed(value) match {
      case Left(message)    => errorProperty.set(Some(message))
      case Right(converted) =>
        errorProperty.set(None)
        val row    = cell.indexProperty.get
        val column = cell.tableColumn
        Option(cell.tableView).foreach { table =>
          table.updateEdit(converted)
          if (table.commitEdit())
            backwards match {
              case Some(reverse) =>
                Option(column).fold(table.focusGridAfterEditor())(
                  table.moveFocusAfterEdit(row, _, reverse)
                )
              case None => table.focusGridAfterEditor()
            }
        }
    }

  private[table] def cancel(): Unit =
    Option(cell.tableView).foreach { table =>
      if (table.cancelEdit()) table.focusGridAfterEditor()
    }

  private[table] def blurred(value: String): Unit =
    if (cell.editing)
      blurPolicy match {
        case TableTextFieldCell.BlurPolicy.Keep   => ()
        case TableTextFieldCell.BlurPolicy.Commit => commit(value, None)
        case TableTextFieldCell.BlurPolicy.Cancel => cancel()
      }

  private def parsed(value: String): Either[String, T] =
    try
      parser(value).left.map(message =>
        Option(message).filter(_.nonEmpty).getOrElse("Invalid value")
      )
    catch {
      case NonFatal(error) =>
        Left(Option(error.getMessage).filter(_.nonEmpty).getOrElse("Invalid value"))
    }

  private def initialText: String =
    Option(cell.tableView)
      .flatMap(table => Option(table.editingValueProperty.get))
      .orElse(Option(cell.itemProperty.get))
      .fold("")(value => formatter(value.asInstanceOf[T]))
}

private object TableTextFieldEditor {
  private var sequence = 0L

  def nextErrorId(): String = {
    sequence += 1
    val id = s"ui-table-text-field-error-$sequence"
    if (dom.document.getElementById(id) == null) id else nextErrorId()
  }
}

