package ui.editor

import ui.core.component.AbstractComponent
import ui.core.i18n.{I18nLocale, I18nResolver, I18nRuntime, MessageCatalog, RuntimeMessage}
import ui.core.state.{Property, ReadOnlyProperty}

/** Resolves native Ember labels and detached Viewport dialogs in the owning editor's locale. */
private[editor] final class EditorText(val runtime: I18nRuntime) {
  def now(message: RuntimeMessage): String                    = runtime.resolveNow(message)
  def text(message: RuntimeMessage): ReadOnlyProperty[String] = runtime.text(message)
}

private[editor] object EditorText {
  def fallback: EditorText = new EditorText(
    I18nRuntime(Property(I18nLocale.En), I18nResolver(MessageCatalog.empty))
  )
  def apply(owner: AbstractComponent): EditorText =
    new EditorText(
      I18nRuntime
        .current(using owner)
        .getOrElse(
          I18nRuntime(Property(I18nLocale.En), I18nResolver(MessageCatalog.empty))
        )
    )
}
