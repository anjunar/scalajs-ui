package ui.bridge

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util
import scala.scalajs.js.annotation.{JSExport, JSExportAll, JSExportTopLevel}
object JavaTimeBridge {
  // moduleID "forms": date fields (scalajs-ui-forms) are what actually calls these, and giving
  // them the same moduleID as FormsRuntime.install() (BridgeRuntime.scala) means both land in the
  // one forms.js file a forms-only consumer needs, instead of forcing scala-java-time and its
  // locale data into every consumer's shared main.js regardless of whether they use dates.
  @JSExportTopLevel("parseLocalDate", "forms")
  def parseLocalDate(value: String): JsLocalDate = new JsLocalDate(LocalDate.parse(value))

  @JSExportTopLevel("parseInstant", "forms")
  def parseInstant(value: String): JsInstant = new JsInstant(Instant.parse(value))

  @JSExportTopLevel("parseLocalDateTime", "forms")
  def parseLocalDateTime(value: String): JsLocalDateTime =
    new JsLocalDateTime(LocalDateTime.parse(value))
}
