package ui.router

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.i18n.I18nLocale
import ui.core.state.ReadOnlyProperty

/** @param locale
  *   The locale `hrefForAppPath` localizes with, when URLs carry one. Links re-read their href when
  *   it changes.
  */
final case class RouterLinkHandler(
    navigate: String => Unit,
    currentPath: ReadOnlyProperty[String],
    hrefForAppPath: String => String,
    appPathFor: String => String = RouterUrlResolver.normalizePath,
    locale: Option[ReadOnlyProperty[I18nLocale]] = None
)

object RouterLinkHandler {
  private val RouterLinkContext =
    Context.create[RouterLinkHandler]("AppRouterLink")

  def provide(handler: RouterLinkHandler)(using component: AbstractComponent): Unit =
    RouterLinkContext.provide(handler)

  def inject(using component: AbstractComponent): Option[RouterLinkHandler] =
    RouterLinkContext.inject
}
