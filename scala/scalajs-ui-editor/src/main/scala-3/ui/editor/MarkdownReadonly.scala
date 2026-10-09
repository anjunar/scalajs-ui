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

/** The readonly SSR/no-JavaScript presentation owned by [[Editor]]. */
private final class MarkdownReadonly(valueProperty: Property[String], policy: MediaUrlPolicy)
    extends AbstractComponent {
  override val tagName: String = "div"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = Seq("scalajs-ui-editor__readonly")
      div {
        classes = Seq("scalajs-ui-editor__preview", "scalajs-ui-editor-readonly")
        setDslAttribute("aria-readonly", "true")
        dynamic(valueProperty.map[AbstractComponent](value => new MarkdownRenderer(value, policy)))
      }
    }
}
