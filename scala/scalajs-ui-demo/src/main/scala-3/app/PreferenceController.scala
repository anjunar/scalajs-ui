package app

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Disposable, Property, ReadOnlyProperty}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
@js.native
trait PreferenceController extends js.Object {
  def getState(): PreferenceState                                                  = js.native
  def setDesign(value: String): Unit                                               = js.native
  def setColorScheme(value: String): Unit                                          = js.native
  def subscribe(listener: js.Function1[PreferenceState, Unit]): js.Function0[Unit] = js.native
}
