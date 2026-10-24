package ui.core.i18n

final case class CatalogEntryBuilder(key: MessageKey) {
  def translations(values: (I18nLocale, String)*): CatalogEntry =
    CatalogEntry(
      key,
      MessageValue(values.map { case (locale, text) => locale -> LocalizedPattern(text) }.toMap)
    )
}
