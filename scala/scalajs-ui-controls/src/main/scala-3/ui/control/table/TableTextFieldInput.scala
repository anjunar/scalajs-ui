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

