package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.Date
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex

final case class AssertTrueValidator(message: String | scala.Null = null) extends MessageValidator[Boolean] {
  override protected def defaultMessage: RuntimeMessage = i18n"Must be true"

  def validate(value: Boolean): Option[String] = Option.when(!value)(resolvedMessage)
}

