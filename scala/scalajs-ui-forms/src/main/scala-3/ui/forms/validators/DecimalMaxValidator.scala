package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex
import scala.Null

final case class DecimalMaxValidator[V](
    value: BigDecimal,
    inclusive: Boolean = true,
    message: String | Null = null
) extends MessageValidator[V] {
  override protected def defaultMessage: RuntimeMessage =
    if (inclusive) i18n"Must be less than or equal to $value" else i18n"Must be less than $value"

  def validate(candidate: V): Option[String] =
    ValidatorSupport.decimalConstraint(
      candidate,
      decimal => if (inclusive) decimal <= value else decimal < value,
      resolvedMessage
    )
}
