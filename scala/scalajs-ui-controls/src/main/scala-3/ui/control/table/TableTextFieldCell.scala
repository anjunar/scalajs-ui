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

private final class TableTextFieldDisplay[S, T](cell: TableCell[S, T], formatter: T => String)
    extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-text-field-cell__display")
    text(cell.itemProperty.map {
      case null  => ""
      case value => formatter(value.asInstanceOf[T])
    }) {}
  }
}

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

private final class TableTextFieldInput[S, T](
    editor: TableTextFieldEditor[S, T],
    initialValue: String
) extends AbstractComponent {
  override val tagName: String = "input"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-text-field-cell__editor")
    setAttribute("type", "text")
    Option(editor.cell.tableColumn).foreach(column => setAttribute("aria-label", column.text))
    setProperty("value", initialValue)
    addDisposable(editor.errorProperty.observe {
      case Some(_) =>
        setAttribute("aria-invalid", "true")
        if (editor.errorId.nonEmpty) setAttribute("aria-errormessage", editor.errorId)
      case None =>
        setAttribute("aria-invalid", "false")
        removeAttribute("aria-errormessage")
    })

    on("input") { event =>
      event.raw match {
        case raw: dom.Event =>
          raw.target match {
            case input: dom.HTMLInputElement => editor.inputChanged(input.value)
            case _                           => ()
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
              editor.commit(nativeValue, None)
            case "Escape" =>
              key.preventDefault(); key.stopPropagation()
              editor.cancel()
            case "Tab" =>
              key.preventDefault(); key.stopPropagation()
              editor.commit(nativeValue, Some(key.shiftKey))
            case _ => ()
          }
        case _ => ()
      }
    }
    on("blur")(_ => editor.blurred(nativeValue))
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser)
      host.asInstanceOf[DomHostElement].node match {
        case input: dom.HTMLInputElement =>
          input.focus(js.Dynamic.literal(preventScroll = true).asInstanceOf[dom.FocusOptions])
          input.select()
        case _ => ()
      }

  private def nativeValue: String =
    host
      .property[js.Any]("value")
      .filter(value => value != null && !js.isUndefined(value))
      .fold("")(_.toString)
}

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
