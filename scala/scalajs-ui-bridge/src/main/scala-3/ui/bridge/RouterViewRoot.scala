package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.layout.{Anchor, TextComponent}
import ui.core.dsl.DslLayer
import ui.core.render.Cursor
import ui.router.{Route, RouteContext as CoreRouteContext, RouteFailure, Router, RouterConfig, RouterLink}

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JavaScriptException
import scala.scalajs.js.JSConverters.*

/** The component `router()` mounts: it owns one `ui.router.Router` and lets the application shell
  * place it.
  *
  * This is what `app.App.compose` assembles by hand on the Scala side --
  * `Router.provide(appRouter)`, then a sidebar of `routerLink`s, then `child(appRouter)`. A Scala
  * user writes that directly; a TypeScript user goes through `router(routes, config, shell)`, so
  * the assembly lives here. The shell body runs with the router in context and receives a scoped
  * outlet body that can mount the routed page inside a Drawer, Viewport or other layout. Raw bridge
  * callers without that layout callback retain the original sibling rendering order.
  */
private[bridge] final class RouterViewRoot(
    routerComponent: Router,
    shell: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit],
    layout: Option[
      js.Function2[js.Function1[ScopeHandleBridge, Unit], ScopeHandleBridge, Unit]
    ]
) extends AbstractCustomComponent {

  override def compose(cursor: Cursor): Unit = {
    Router.provide(routerComponent)(using this)

    DslLayer.render(this, cursor) {
      layout match {
        case Some(composeLayout) =>
          val outlet: js.Function1[ScopeHandleBridge, Unit] = { scope =>
            given AbstractComponent = scope.parent
            given Cursor            = scope.cursor
            DslLayer.child(routerComponent) {}
            ()
          }
          composeLayout(outlet, new ScopeHandleBridge(this, cursor))

        case None =>
          // Raw bridge callers predating the layout callback keep the original sibling shape.
          shell(new ComponentHandleBridge(this), new ScopeHandleBridge(this, cursor))
          DslLayer.child(routerComponent) {}
      }
    }
  }
}
