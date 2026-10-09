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

/** The ordinary link used to change editor mode with or without JavaScript. */
private[editor] final class MarkdownModeLink(
    url: String,
    label: ReadOnlyProperty[String],
    readonly: Boolean,
    onActivate: () => Unit
) extends AbstractComponent {
  def this(url: String, label: String, readonly: Boolean, onActivate: () => Unit) =
    this(url, Property(label), readonly, onActivate)

  override val tagName: String = "a"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      val safeUrl = MarkdownSecurity.safeLinkUrl(url)
      classes = Seq("scalajs-ui-editor__mode-toggle", "scalajs-ui-editor__edit-link") ++
        Option.when(readonly)("scalajs-ui-editor__readonly-link")
      setDslAttribute("href", safeUrl)
      text(label) {}
      if (cursor.isBrowser && isInternalDestination(safeUrl))
        onClick { event =>
          event.preventDefault()
          onActivate()
          UrlScope.current(using this) match {
            case Some(scope) => scope.navigate(safeUrl, replace = false)
            case None        => dom.window.history.pushState(null, "", safeUrl)
          }
        }
    }

  private def isInternalDestination(destination: String): Boolean =
    (destination.startsWith("/") && !destination.startsWith("//")) ||
      destination.startsWith("?") ||
      destination.startsWith("#")
}
