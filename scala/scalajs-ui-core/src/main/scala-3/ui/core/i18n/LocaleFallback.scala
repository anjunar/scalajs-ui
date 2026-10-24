package ui.core.i18n

final case class LocaleFallback(primary: I18nLocale, defaultLocale: I18nLocale = I18nLocale.En) {
  def chain: Vector[I18nLocale] = {
    val parents =
      Iterator.iterate(primary.parent)(_.flatMap(_.parent)).takeWhile(_.isDefined).flatten
    (Iterator.single(primary) ++ parents ++ Iterator.single(defaultLocale)).toVector.distinct
  }
}
