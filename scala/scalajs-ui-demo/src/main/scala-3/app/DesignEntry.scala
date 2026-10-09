package app

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Disposable, Property, ReadOnlyProperty}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
trait DesignEntry extends js.Object {
  val id: String   = js.native
  val name: String = js.native
}
