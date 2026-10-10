package ui.bridge

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util
import scala.scalajs.js.annotation.{JSExport, JSExportAll, JSExportTopLevel}

@JSExportAll
final class JsLocalDateTime private[bridge] (private val underlying: LocalDateTime) {
  def year: Int                                            = underlying.getYear
  def monthValue: Int                                      = underlying.getMonthValue
  def dayOfMonth: Int                                      = underlying.getDayOfMonth
  def hour: Int                                            = underlying.getHour
  def minute: Int                                          = underlying.getMinute
  override def toString: String                            = underlying.toString
  def format(pattern: String, languageTag: String): String =
    underlying.format(DateTimeFormatter.ofPattern(pattern, util.Locale.forLanguageTag(languageTag)))
}
