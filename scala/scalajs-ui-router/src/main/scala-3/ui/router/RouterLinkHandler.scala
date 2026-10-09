package ui.router

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.dsl.DslLayer
import ui.core.layout.Anchor
import ui.core.render.Cursor
import ui.core.state.ReadOnlyProperty

final case class RouterLinkHandler(
    navigate: String => Unit,
    currentPath: ReadOnlyProperty[String],
    hrefForAppPath: String => String,
    appPathFor: String => String = RouterUrlResolver.normalizePath
)

object RouterLinkHandler {
  private val RouterLinkContext =
    Context.create[RouterLinkHandler]("AppRouterLink")

  def provide(handler: RouterLinkHandler)(using component: AbstractComponent): Unit =
    RouterLinkContext.provide(handler)

  def inject(using component: AbstractComponent): Option[RouterLinkHandler] =
    RouterLinkContext.inject
}

