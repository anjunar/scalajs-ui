package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex
import java.math.{BigDecimal as JavaBigDecimal}

private object ValidatorSupport {
  val defaultEmailRegex: Regex =
    "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$".r

  def sizeOf(value: Any): Option[Int] = value match {
    case text: String            => Some(text.length)
    case array: js.Array[?]      => Some(array.length)
    case array: Array[?]         => Some(array.length)
    case iterable: Iterable[?]   => Some(iterable.size)
    case values: IterableOnce[?] => Some(values.iterator.length)
    case _                       => None
  }

  def longConstraint[V](candidate: V, predicate: Long => Boolean, message: String): Option[String] =
    if (candidate == null) None
    else
      toBigDecimal(candidate) match {
        case Some(decimal) if decimal.isValidLong && predicate(decimal.toLongExact) => None
        case Some(_)                                                                => Some(message)
        case None                                                                   => None
      }

  def decimalConstraint[V](
      candidate: V,
      predicate: BigDecimal => Boolean,
      message: String
  ): Option[String] =
    if (candidate == null) None
    else
      toBigDecimal(candidate) match {
        case Some(decimal) if predicate(decimal) => None
        case Some(_)                             => Some(message)
        case None                                => None
      }

  def toBigDecimal(value: Any): Option[BigDecimal] = value match {
    case big: BigDecimal                                      => Some(big)
    case big: JavaBigDecimal                                  => Some(BigDecimal(big))
    case number: Byte                                         => Some(BigDecimal(number))
    case number: Short                                        => Some(BigDecimal(number))
    case number: Int                                          => Some(BigDecimal(number))
    case number: Long                                         => Some(BigDecimal(number))
    case number: Float if !number.isNaN && !number.isInfinite =>
      Some(BigDecimal.decimal(number.toDouble))
    case number: Double if !number.isNaN && !number.isInfinite => Some(BigDecimal(number))
    // Exact, so a long integer keeps its digits; "NaN", "Infinity" and "1e400" are no numbers
    // here, where the Double route would either throw or lose precision.
    case text: String if text.trim.nonEmpty => Try(BigDecimal(text.trim)).toOption
    case _                                  => None
  }

  def temporalConstraint[V](
      candidate: V,
      isPast: Boolean,
      inclusive: Boolean,
      message: String
  ): Option[String] =
    if (candidate == null || candidate == "") None
    else
      temporalComparison(candidate) match {
        case Some(comparison) if accepted(comparison, isPast, inclusive) => None
        case Some(_)                                                     => Some(message)
        case None if candidate.isInstanceOf[String]                      => Some(message)
        case None                                                        => None
      }

  private def temporalComparison(candidate: Any): Option[Int] = candidate match {
    case value: Instant        => Some(value.compareTo(Instant.now()))
    case value: LocalDate      => Some(value.compareTo(localToday()))
    case value: LocalDateTime  => Some(value.compareTo(localNow()))
    case value: OffsetDateTime => Some(value.compareTo(OffsetDateTime.now()))
    case value: ZonedDateTime  => Some(value.compareTo(ZonedDateTime.now()))
    case value: util.Date      => Some(value.compareTo(new util.Date()))
    // HTML date and datetime-local inputs bind ISO local strings through Control[String]; the
    // offset form covers a value that round-tripped through JSON.
    case value: String =>
      Try(LocalDate.parse(value)).toOption
        .map(_.compareTo(localToday()))
        .orElse(Try(LocalDateTime.parse(value)).toOption.map(_.compareTo(localNow())))
        .orElse(
          Try(OffsetDateTime.parse(value)).toOption.map(_.toInstant.compareTo(Instant.now()))
        )
    case _ => None
  }

  // The JS host owns the local calendar/time zone. Reading its components avoids requiring a
  // separate IANA time-zone database for local-time comparisons.
  private def localNow(): LocalDateTime = {
    val now = new js.Date()
    LocalDateTime.of(
      now.getFullYear().toInt,
      now.getMonth().toInt + 1,
      now.getDate().toInt,
      now.getHours().toInt,
      now.getMinutes().toInt,
      now.getSeconds().toInt,
      now.getMilliseconds().toInt * 1000000
    )
  }

  private def localToday(): LocalDate = localNow().toLocalDate

  private def accepted(comparison: Int, isPast: Boolean, inclusive: Boolean): Boolean =
    if (isPast) {
      if (inclusive) comparison <= 0 else comparison < 0
    } else if (inclusive) comparison >= 0
    else comparison > 0
}
