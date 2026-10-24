package ui.core.i18n

final case class MessageValue(
    translations: Map[I18nLocale, LocalizedPattern],
    previousSources: Vector[StaleSource] = Vector.empty,
    state: MessageState = MessageState.Current
) {
  def at(locale: I18nLocale): Option[LocalizedPattern] =
    translations.get(locale)
}
