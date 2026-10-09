package ui.forms.validators

import ui.core.i18n.{I18n, I18nLocale, I18nResolver, MessageCatalog, RuntimeMessage}

trait Validator[-V] {
  def validate(value: V): Option[String]

  /** Controls resolve this message in their own component tree, including after a locale change. */
  def validateMessage(value: V): Option[RuntimeMessage] =
    validate(value).filter(_.nonEmpty).map(I18n.literal)
}
