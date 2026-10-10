package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex
import scala.Null

final case class FutureValidator[V](message: String | Null = null) extends MessageValidator[V] {
  override protected def defaultMessage: RuntimeMessage = i18n"Must be in the future"

  def validate(value: V): Option[String] =
    ValidatorSupport.temporalConstraint(value, isPast = false, inclusive = false, resolvedMessage)
}
