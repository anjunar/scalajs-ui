package app

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Disposable, Property, ReadOnlyProperty}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
@js.native @JSImport("@anjunar/scalajs-ui/preferences", JSImport.Namespace)
object DesignPreferences extends js.Object {
  val designs: js.Array[DesignEntry]                                          = js.native
  def createPreferences(legacyKey: String, url: String): PreferenceController = js.native
  def serverPreferences(url: String): PreferenceState                         = js.native
  def bootstrapScript(legacyKey: String): String                              = js.native
}
