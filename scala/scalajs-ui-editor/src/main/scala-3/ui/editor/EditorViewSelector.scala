package ui.editor

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.layout.Span.span
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.{Property, ReadOnlyProperty}
import ui.forms.SelectInput.selectInput
import ui.forms.SelectOption

/** A persistent toolbar control, also available while the native editing surface is hidden. */
private[editor] final class EditorViewSelector(
    editable: Property[Boolean],
    markdown: Property[Boolean],
    labels: EditorText,
    editLabel: ReadOnlyProperty[String],
    readonlyLabel: ReadOnlyProperty[String]
) extends AbstractComponent {
  val tagName = "label"

  override def compose(cursor: Cursor): Unit = {
    def current  = if (!editable.get) "readonly" else if (markdown.get) "markdown" else "visual"
    val selected = Property(current)
    var synchronizing = false
    def sync(): Unit  = if (!synchronizing) {
      synchronizing = true
      try selected.set(current)
      finally synchronizing = false
    }
    addDisposable(editable.observeWithoutInitial(_ => sync()))
    addDisposable(markdown.observeWithoutInitial(_ => sync()))
    addDisposable(selected.observeWithoutInitial { mode =>
      if (!synchronizing) {
        synchronizing = true
        try
          mode match {
            case "markdown" => markdown.set(true); editable.set(true)
            case "readonly" => editable.set(false); markdown.set(false)
            case "visual"   => editable.set(true); markdown.set(false)
            case _          => ()
          }
        finally { synchronizing = false; sync() }
      }
    })
    render(this, cursor) {
      classes = "scalajs-ui-editor__view-selector"
      span { text(labels.text(EditorMessages.view)) {} }
      selectInput(
        "editor-view",
        Seq(
          SelectOption("visual", editLabel),
          SelectOption("readonly", readonlyLabel),
          SelectOption("markdown", labels.text(EditorMessages.markdown))
        ),
        standalone = true
      ) {}.bindValue(selected)
    }
  }
}
