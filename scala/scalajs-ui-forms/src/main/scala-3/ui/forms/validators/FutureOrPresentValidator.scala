package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.Date
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex

final case class FutureOrPresentValidator[V](message: String | scala.Null = null)
    extends MessageValidator[V] {
  override protected def defaultMessage: RuntimeMessage = i18n"Must be in the future or present"

  def validate(value: V): Option[String] =
    ValidatorSupport.temporalConstraint(value, isPast = false, inclusive = true, resolvedMessage)
}

