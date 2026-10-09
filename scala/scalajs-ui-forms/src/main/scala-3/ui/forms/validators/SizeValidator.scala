package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.Date
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex

final case class SizeValidator[V](
    min: Int = 0,
    max: Int = Int.MaxValue,
    message: String | scala.Null = null
) extends MessageValidator[V] {
  require(min >= 0, s"min must be >= 0 but was $min")
  require(max >= min, s"max must be >= min but was $max < $min")

  def validate(value: V): Option[String] =
    if (value == null) None
    else
      ValidatorSupport
        .sizeOf(value)
        .filter(size => size < min || size > max)
        .map(_ => resolvedMessage)

  override protected def defaultMessage: RuntimeMessage =
    if (min == max) i18n"Must contain exactly $min characters/items"
    else if (max == Int.MaxValue) i18n"Must contain at least $min characters/items"
    else if (min == 0) i18n"Must contain at most $max characters/items"
    else i18n"Must contain between $min and $max characters/items"
}

