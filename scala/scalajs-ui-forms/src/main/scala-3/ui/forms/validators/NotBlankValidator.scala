package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex
import scala.Null

final case class NotBlankValidator(message: String | Null = null) extends MessageValidator[String] {
  override protected def defaultMessage: RuntimeMessage = i18n"Must not be blank"

  def validate(value: String): Option[String] =
    Option.when(value == null || value.trim.isEmpty)(resolvedMessage)
}
