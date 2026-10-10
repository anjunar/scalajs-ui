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
import ui.router.RouterState

/** `router` -- mounts a [[RouterViewRoot]] around a `ui.router.Router` with the translated table.
  */
private[bridge] object RouterFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    given ExecutionContext = ExecutionContext.global

    val routes = options("routes").asInstanceOf[js.Array[RouteFacade]]
    val config = options.get("config").map(_.asInstanceOf[RouterConfigFacade]).orUndefined
    val layout = options
      .get("layout")
      .map(
        _.asInstanceOf[
          js.Function2[js.Function1[ScopeHandleBridge, Unit], ScopeHandleBridge, Unit]
        ]
      )

    val startUrl =
      config.toOption
        .flatMap(_.initialUrl.toOption)
        .getOrElse(cursor.browserUrl.getOrElse("/"))

    val routerComponent =
      new Router(
        RouterFactories.buildRoutes(routes),
        startUrl,
        RouterFactories.projectConfig(config)
      )

    def pageEvent(state: RouterState): js.Object =
      js.Dynamic.literal(path = state.path, url = state.url)

    config.toOption.foreach { callbacks =>
      callbacks.onPageLoad.foreach { listener =>
        routerComponent.onPageLoad(state => listener(pageEvent(state)))
      }
      callbacks.onPageResolved.foreach { listener =>
        routerComponent.onPageResolved(state => listener(pageEvent(state)))
      }
    }

    // SSR only: let the response carry an error route's own status. No-op under `mount`/`hydrate`,
    // which never open a slot.
    SsrStatus.current.foreach(_.bind(() => routerComponent.responseStatus.get))

    DslLayer.child(new RouterViewRoot(routerComponent, body, layout)) {}
  }
}
