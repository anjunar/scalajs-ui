package app

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Disposable, Property, ReadOnlyProperty}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** Per-application preference state; the small shared controller owns browser effects. */
final class AppTheme private (initialUrl: String) {
  private val controller =
    DesignPreferences.createPreferences(SiteConfig.themeStorageKey, initialUrl)
  val serverState: PreferenceState = DesignPreferences.serverPreferences(initialUrl)
  private val mode                 = Property(
    AppTheme.Mode.parse(controller.getState().colorScheme).getOrElse(AppTheme.Mode.Dark)
  )
  private val design                                      = Property(controller.getState().design)
  private val storageAvailable                            = Property(true)
  def modeProperty: ReadOnlyProperty[AppTheme.Mode]       = mode
  def designProperty: ReadOnlyProperty[String]            = design
  def storageAvailableProperty: ReadOnlyProperty[Boolean] = storageAvailable

  def install(owner: AbstractComponent): Unit = {
    val cancel = controller.subscribe { state =>
      mode.set(AppTheme.Mode.parse(state.colorScheme).getOrElse(AppTheme.Mode.Dark))
      design.set(state.design)
      storageAvailable.set(state.storageAvailable)
    }
    owner.addDisposable(Disposable(cancel()))
  }
  def set(value: AppTheme.Mode): Unit = controller.setColorScheme(value.value)
  def setDesign(value: String): Unit  = controller.setDesign(value)
}

object AppTheme {
  enum Mode(val value: String) {
    case Light extends Mode("light")
    case Dark  extends Mode("dark")
  }
  object Mode {
    def parse(value: String | Null): Option[Mode] = value match {
      case "light" => Some(Mode.Light)
      case "dark"  => Some(Mode.Dark)
      case _       => None
    }
  }
  def forEnvironment(initialUrl: String = "/"): AppTheme = new AppTheme(initialUrl)
  private val Value: Context[AppTheme]                   = Context.create[AppTheme]("AppTheme")
  def provide(value: AppTheme)(using component: AbstractComponent): Unit = Value.provide(value)
  def current(using component: AbstractComponent): Option[AppTheme]      = Value.inject
  def require(using component: AbstractComponent): AppTheme              = current.getOrElse {
    throw new IllegalStateException("No AppTheme found in the current component tree.")
  }
}
