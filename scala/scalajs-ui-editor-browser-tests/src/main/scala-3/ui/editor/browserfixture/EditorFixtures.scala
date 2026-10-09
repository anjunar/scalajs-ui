package ui.editor.browserfixture

import org.scalajs.dom
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.i18n.{I18n, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog}
import ui.core.render.{Cursor, DomCursor}
import ui.core.state.Property
import ui.editor.{Editor, EditorMessages, MediaUploadStatus, NativeEditorBinding}
import ui.viewport.Viewport.viewport

/** Browser test application only; exercises the real editor and its application I18n context. */
@JSExportTopLevel("editorFixtures")
object EditorFixtures {
  private var root: AbstractComponent = null
  private var control: Editor         = null
  private var binding: NativeEditorBinding = null
  private var mountedSessions         = 0
  private val locale                  = Property(I18nLocale.En)
  private val language                = I18nRuntime(
    locale,
    I18nResolver(
      MessageCatalog(
        I18n.entry(EditorMessages.undo.key).translations(I18nLocale("de")     -> "Rückgängig"),
        I18n.entry(EditorMessages.redo.key).translations(I18nLocale("de")     -> "Wiederholen"),
        I18n.entry(EditorMessages.bold.key).translations(I18nLocale("de")     -> "Fett"),
        I18n.entry(EditorMessages.history.key).translations(I18nLocale("de")  -> "Verlauf"),
        I18n.entry(EditorMessages.editText.key).translations(I18nLocale("de") -> "Text bearbeiten"),
        I18n.entry(EditorMessages.editLink.key).translations(I18nLocale("de") -> "Link bearbeiten"),
        I18n.entry(EditorMessages.address.key).translations(I18nLocale("de")  -> "Adresse"),
        I18n.entry(EditorMessages.title.key).translations(I18nLocale("de")    -> "Titel"),
        I18n.entry(EditorMessages.apply.key).translations(I18nLocale("de")    -> "Übernehmen"),
        I18n.entry(EditorMessages.cancel.key).translations(I18nLocale("de")   -> "Abbrechen"),
        I18n
          .entry(EditorMessages.uploading(0).key)
          .translations(I18nLocale("de") -> "{count} Bilder werden hochgeladen")
      )
    )
  )

  @JSExport def mount(container: dom.Element, initialLocale: String): Unit = {
    dispose()
    mountedSessions = 0
    locale.set(I18nLocale(Option(initialLocale).filter(_.nonEmpty).getOrElse("en")))
    root = Runtime.mount(
      new AbstractComponent {
        val tagName                                = "main"
        override def compose(cursor: Cursor): Unit = render(this, cursor) {
          if (initialLocale.nonEmpty) I18nRuntime.provide(language)
          viewport {
            control = Editor.editor("body", standalone = true) {
              Editor.value = "Original text"
              summon[Editor].onNativeSession = Some(value => { binding = value; mountedSessions += 1 })
            }
          }
        }
      },
      DomCursor.root(container)
    )
  }

  @JSExport def setLocale(code: String): Unit = language.setLocale(I18nLocale(code))
  @JSExport def value(): String               = control.valueProperty.get
  @JSExport def selection(): String = binding.session.selection.toString
  @JSExport def sessions(): Int               = mountedSessions
  @JSExport def pending(count: Int): Unit     =
    control.mediaStatusProperty.set(MediaUploadStatus(pending = count))
  @JSExport def dispose(): Unit = {
    if (root != null) { Runtime.unmount(root); root = null }
  }
}
