package ui.core.remote

import ui.core.state.ListProperty
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try
final case class RemoteSort(field: String, ascending: Boolean = true) {
  def direction: String    = if (ascending) "asc" else "desc"
  def asQueryValue: String = s"$field,$direction"
}
