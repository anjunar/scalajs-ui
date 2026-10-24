package ui.core.i18n

final case class I18nConfig(
    resolver: I18nResolver,
    supportedLocales: Seq[I18nLocale],
    defaultLocale: I18nLocale = I18nLocale.En
) {
  require(supportedLocales.nonEmpty, "I18nConfig.supportedLocales must not be empty")
  require(
    supportedLocales.contains(defaultLocale),
    "I18nConfig.defaultLocale must be part of supportedLocales"
  )

  val localesByCode: Map[String, I18nLocale] =
    supportedLocales.iterator.map(locale => locale.code -> locale).toMap
}
