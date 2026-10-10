package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher

final case class MessageValue(
    translations: Map[I18nLocale, LocalizedPattern],
    previousSources: Vector[StaleSource] = Vector.empty,
    state: MessageState = MessageState.Current
) {
  def at(locale: I18nLocale): Option[LocalizedPattern] =
    translations.get(locale)
}
