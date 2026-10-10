package ui.bridge

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util
import scala.scalajs.js.annotation.{JSExport, JSExportAll, JSExportTopLevel}

@JSExportAll
final class JsInstant private[bridge] (private val underlying: Instant) {
  def epochMilli: Double        = underlying.toEpochMilli.toDouble
  override def toString: String = underlying.toString
  def format(pattern: String, languageTag: String, zoneId: String): String =
    DateTimeFormatter
      .ofPattern(pattern, util.Locale.forLanguageTag(languageTag))
      .withZone(ZoneId.of(zoneId))
      .format(underlying)
}
