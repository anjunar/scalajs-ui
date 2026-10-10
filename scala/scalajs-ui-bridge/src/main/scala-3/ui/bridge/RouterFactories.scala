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
import scala.concurrent.Future
private[bridge] object RouterFactories {

  def failureKind(failure: RouteFailure): String =
    failure match {
      case _: RouteFailure.NotMatched => "not-matched"
      case _: RouteFailure.LoadFailed => "load-failed"
    }

  def buildRoutes(defs: js.Array[RouteFacade])(using ExecutionContext): Seq[Route] =
    defs.toSeq.map(buildRoute)

  private def routeComponent(jsBody: js.Function1[ScopeHandleBridge, Unit]): AbstractComponent =
    Route.component {
      jsBody(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
    }

  private def buildRoute(facade: RouteFacade)(using ec: ExecutionContext): Route =
    Route(
      path = facade.path,
      load = context => {
        val produced = facade.load(new RouteContextHandle(context))

        // A synchronous loader stays synchronous: `Future.successful` with a settled `value`, so
        // `Router.loadRoute` renders in one pass instead of flashing the loading boundary. Only a
        // real promise goes through `.map`, which the global EC always defers.
        if (js.typeOf(produced) == "function")
          Future.successful(
            routeComponent(produced.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
          )
        else
          produced
            .asInstanceOf[js.Promise[js.Function1[ScopeHandleBridge, Unit]]]
            .toFuture
            .map(routeComponent)
      },
      constraints = facade.constraints.toOption
        .map(_.view.mapValues(fn => (value: String) => fn(value)).toMap)
        .getOrElse(Map.empty),
      children = facade.children.toOption.map(buildRoutes).getOrElse(Nil),
      status = facade.status.getOrElse(200)
    )

  def projectConfig(facade: js.UndefOr[RouterConfigFacade]): RouterConfig =
    facade.toOption match {
      case None         => RouterConfig()
      case Some(config) =>
        val base = RouterConfig()

        base.copy(
          basePath = config.basePath.getOrElse(base.basePath),
          onFailure = config.onFailure.toOption match {
            case None     => base.onFailure
            case Some(fn) =>
              failure => {
                val projected = js.Dynamic.literal(
                  kind = failureKind(failure),
                  path = failure.state.browserPath
                )
                failure match {
                  case RouteFailure.LoadFailed(JavaScriptException(error), _) =>
                    projected.updateDynamic("error")(error.asInstanceOf[js.Any])
                  case RouteFailure.LoadFailed(error, _) =>
                    projected.updateDynamic("error")(error.getMessage)
                  case _: RouteFailure.NotMatched => ()
                }
                val result = fn(projected.asInstanceOf[js.Object])
                if (js.isUndefined(result) || result == null) None
                else Some(result.asInstanceOf[String])
              }
          },
          renderErrorsOnServer = config.renderErrorsOnServer.getOrElse(base.renderErrorsOnServer)
        )
    }
}
