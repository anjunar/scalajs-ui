package app

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Disposable, Property, ReadOnlyProperty}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
@js.native
trait PreferenceState extends js.Object {
  val design: String            = js.native
  val colorScheme: String       = js.native
  val storageAvailable: Boolean = js.native
}
