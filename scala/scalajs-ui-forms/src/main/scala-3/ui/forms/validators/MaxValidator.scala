package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex
import scala.Null

final case class MaxValidator[V](value: Long, message: String | Null = null)
    extends MessageValidator[V] {
  override protected def defaultMessage: RuntimeMessage = i18n"Must be less than or equal to $value"

  def validate(candidate: V): Option[String] =
    ValidatorSupport.longConstraint(
      candidate,
      _ <= value,
      resolvedMessage
    )
}
