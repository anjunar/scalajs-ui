package ui.editor

import ui.core.component.AbstractComponent
import ui.core.context.UrlScope
import ui.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.statement.DynamicComponentRenderer.dynamic
import org.scalajs.dom
import org.scalajs.dom.HTMLTextAreaElement

/** The editable SSR/no-JavaScript presentation owned by [[Editor]]. */
private final class MarkdownTextArea(
    name: String,
    valueProperty: Property[String],
    placeholderProperty: Property[String],
    onMarkdownChanged: String => Unit,
    onFocusChanged: Boolean => Unit
) extends AbstractComponent {
  override val tagName: String = "textarea"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = Seq("scalajs-ui-editor__markdown-textarea")
      setDslAttribute("name", name)
      setDslAttribute("aria-label", name)
      setDslAttribute("aria-multiline", "true")
      setDslAttribute("spellcheck", "true")
      Option(placeholderProperty.get).filter(_.nonEmpty).foreach(setDslAttribute("placeholder", _))

      text(valueProperty) {}

      on("input") { event =>
        event.raw match {
          case domEvent: dom.Event =>
            domEvent.target match {
              case textarea: HTMLTextAreaElement => onMarkdownChanged(textarea.value)
              case _                             => ()
            }
          case _ => ()
        }
      }
      on("focus") { _ => onFocusChanged(true) }
      on("blur") { _ => onFocusChanged(false) }

      if (cursor.isBrowser)
        // The text child already initializes the value. An eager property write would erase
        // edits made to the server-rendered textarea before hydration.
        addDisposable(valueProperty.observeWithoutInitial(value => setProperty("value", value)))
    }
}
