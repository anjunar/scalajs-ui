package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex
import scala.Null

final case class DigitsValidator[V](
    integer: Int,
    fraction: Int,
    message: String | Null = null
) extends MessageValidator[V] {
  override protected def defaultMessage: RuntimeMessage =
    i18n"At most $integer integer digits and $fraction fractional digits are allowed"

  require(integer >= 0, s"integer must be >= 0 but was $integer")
  require(fraction >= 0, s"fraction must be >= 0 but was $fraction")

  def validate(candidate: V): Option[String] =
    if (candidate == null) None
    else
      ValidatorSupport.toBigDecimal(candidate) match {
        case Some(decimal) if validDigits(decimal) => None
        case Some(_)                               =>
          Some(
            resolvedMessage
          )
        case None => None
      }

  private def validDigits(decimal: BigDecimal): Boolean = {
    val normalized    = decimal.bigDecimal.stripTrailingZeros()
    val scale         = math.max(0, normalized.scale())
    val integerDigits = math.max(0, normalized.precision() - scale)
    integerDigits <= integer && scale <= fraction
  }
}
