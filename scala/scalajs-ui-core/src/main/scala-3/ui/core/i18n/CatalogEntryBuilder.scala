package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher

final case class CatalogEntryBuilder(key: MessageKey) {
  def translations(values: (I18nLocale, String)*): CatalogEntry =
    CatalogEntry(
      key,
      MessageValue(values.map { case (locale, text) => locale -> LocalizedPattern(text) }.toMap)
    )
}
