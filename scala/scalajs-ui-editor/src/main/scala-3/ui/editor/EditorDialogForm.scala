package ui.editor

import ember.editor.core.EditorError
import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.i18n.RuntimeMessage
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Label.label
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomNodes}
import ui.core.state.Property
import org.scalajs.dom

/** Ordinary UI content mounted directly by Viewport.WindowConf. */
private[editor] final class EditorDialogForm(
    labels: EditorText,
    fields: Vector[(RuntimeMessage, String)],
    submit: Vector[String] => Either[EditorError, Unit],
    extra: Option[(RuntimeMessage, Vector[String] => Either[EditorError, Unit])],
    close: () => Unit
) extends AbstractComponent {
  val tagName        = "form"
  private val error  = Property("")
  private var inputs = Vector.empty[AbstractComponent]

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = "scalajs-ui-editor-dialog"
      inputs = fields.map { (caption, initial) =>
        var field: AbstractComponent = null
        label {
          text(labels.text(caption)) {}
          field = child(new AbstractComponent { val tagName = "input" }) {
            setDslAttribute("type", "text")
            setDslAttribute("value", initial)
          }
        }
        field
      }
      paragraph {
        setDslAttribute("role", "alert")
        text(error) {}
      }
      button(labels.text(EditorMessages.apply)) {
        buttonType("submit")
      }
      extra.foreach { (caption, action) =>
        button(labels.text(caption)) {
          buttonType("button")
          onClick { _ => complete(action(values)) }
        }
      }
      button(labels.text(EditorMessages.cancel)) {
        buttonType("button")
        onClick { _ => close() }
      }
      on("submit") { event =>
        event.preventDefault()
        complete(submit(values))
      }
      on("keydown") { event =>
        if (event.raw.asInstanceOf[dom.KeyboardEvent].key == "Escape") {
          event.preventDefault()
          event.stopPropagation()
          close()
        }
      }
    }

  override def afterCompose(cursor: Cursor): Unit = if (cursor.isBrowser)
    inputs.headOption.foreach(input =>
      DomNodes.raw(input.host).asInstanceOf[dom.HTMLElement].focus()
    )

  private def values =
    inputs.map(input => DomNodes.raw(input.host).asInstanceOf[dom.HTMLInputElement].value)

  private def complete(result: Either[EditorError, Unit]): Unit =
    result.fold(e => error.set(e.message), _ => close())
}
