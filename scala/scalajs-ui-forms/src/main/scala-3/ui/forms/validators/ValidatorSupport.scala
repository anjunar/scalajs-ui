package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.Date
import ui.core.i18n.{RuntimeMessage, i18n}
import scala.scalajs.js
import scala.util.Try
import scala.util.matching.Regex

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
    case big: java.math.BigDecimal                            => Some(BigDecimal(big))
    case number: Byte                                         => Some(BigDecimal(number))
    case number: Short                                        => Some(BigDecimal(number))
    case number: Int                                          => Some(BigDecimal(number))
    case number: Long                                         => Some(BigDecimal(number))
    case number: Float if !number.isNaN && !number.isInfinite =>
      Some(BigDecimal.decimal(number.toDouble))
    case number: Double if !number.isNaN && !number.isInfinite => Some(BigDecimal(number))
    case text: String if text.trim.nonEmpty => text.trim.toDoubleOption.map(BigDecimal(_))
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
    case value: LocalDateTime  => Some(value.compareTo(LocalDateTime.now()))
    case value: OffsetDateTime => Some(value.compareTo(OffsetDateTime.now()))
    case value: ZonedDateTime  => Some(value.compareTo(ZonedDateTime.now()))
    case value: Date           => Some(value.compareTo(new Date()))
    // HTML date inputs bind ISO local-date strings through Control[String].
    case value: String => Try(LocalDate.parse(value)).toOption.map(_.compareTo(localToday()))
    case _             => None
  }

  // The JS host owns the local calendar/time zone. Reading its date components
  // avoids requiring a separate IANA time-zone database for date-only comparisons.
  private def localToday(): LocalDate = {
    val now = new js.Date()
    LocalDate.of(now.getFullYear().toInt, now.getMonth().toInt + 1, now.getDate().toInt)
  }

  private def accepted(comparison: Int, isPast: Boolean, inclusive: Boolean): Boolean =
    if (isPast) {
      if (inclusive) comparison <= 0 else comparison < 0
    } else if (inclusive) comparison >= 0
    else comparison > 0
}
