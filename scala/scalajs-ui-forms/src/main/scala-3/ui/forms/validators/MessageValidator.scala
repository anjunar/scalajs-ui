package ui.forms.validators

import ui.core.i18n.{I18n, I18nLocale, I18nResolver, MessageCatalog, RuntimeMessage}


/** Built-in validators retain their plain-string API and carry arguments separately for i18n. */
private[validators] trait MessageValidator[-V] extends Validator[V] {
  def message: String | scala.Null
  protected def defaultMessage: RuntimeMessage

  protected def resolvedMessage: String =
    Option(message).filter(_.nonEmpty).getOrElse(MessageValidator.fallback.resolve(defaultMessage, I18nLocale.En))

  override def validateMessage(value: V): Option[RuntimeMessage] =
    validate(value).map(_ => Option(message).filter(_.nonEmpty).map(I18n.literal).getOrElse(defaultMessage))
}

private object MessageValidator {
  val fallback = I18nResolver(MessageCatalog.empty)
}
