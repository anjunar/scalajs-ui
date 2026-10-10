package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex
import scala.Null

final case class NegativeOrZeroValidator[V](message: String | Null = null)
    extends MessageValidator[V] {
  override protected def defaultMessage: RuntimeMessage = i18n"Must be negative or zero"

  def validate(candidate: V): Option[String] =
    ValidatorSupport.decimalConstraint(candidate, _ <= 0, resolvedMessage)
}
