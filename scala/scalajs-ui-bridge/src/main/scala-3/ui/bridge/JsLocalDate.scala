package ui.bridge

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util.Locale
import scala.scalajs.js.annotation.{JSExport, JSExportAll, JSExportTopLevel}

/** Stable JavaScript boundary for the scala-java-time implementation already linked into the UI
  * runtime. The wrappers deliberately retain the real java.time values; TypeScript never depends on
  * Scala.js linker names.
  */
@JSExportAll
final class JsLocalDate private[bridge] (private val underlying: LocalDate) {
  def year: Int                                            = underlying.getYear
  def monthValue: Int                                      = underlying.getMonthValue
  def dayOfMonth: Int                                      = underlying.getDayOfMonth
  override def toString: String                            = underlying.toString
  def format(pattern: String, languageTag: String): String =
    underlying.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(languageTag)))
}
