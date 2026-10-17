package ui.editor

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.render.Cursor
import ui.core.state.ReadOnlyProperty

/** Preserve the existing navigation link for browsers without JavaScript. */
private[editor] final class EditorModeFallback(
    url: String,
    label: ReadOnlyProperty[String],
    readonly: Boolean,
    onActivate: () => Unit
) extends AbstractComponent {
  val tagName                                = "span"
  override def compose(cursor: Cursor): Unit = render(this, cursor) {
    classes = "scalajs-ui-editor__mode-fallback"
    child(new MarkdownModeLink(url, label, readonly, onActivate)) {}
  }
}
