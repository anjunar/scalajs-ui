package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex
import scala.Null

final case class AssertFalseValidator(message: String | Null = null)
    extends MessageValidator[Boolean] {
  override protected def defaultMessage: RuntimeMessage = i18n"Must be false"

  def validate(value: Boolean): Option[String] = Option.when(value)(resolvedMessage)
}
