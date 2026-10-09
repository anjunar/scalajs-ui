package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.layout.{Anchor, TextComponent}
import ui.core.dsl.DslLayer
import ui.core.render.Cursor
import ui.router.{
  Route,
  RouteContext => CoreRouteContext,
  RouteFailure,
  Router,
  RouterConfig,
  RouterLink
}

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JavaScriptException
import scala.scalajs.js.JSConverters.*

/** The JS projection of `ui.router.RouteContext`, handed to a TS route loader.
  *
  * Only the four fields the deleted `router.ts` declared: no `state`, `routeMatch` or `locale` --
  * those are Scala-internal routing types with no TypeScript meaning.
  */
private[bridge] final class RouteContextHandle(source: CoreRouteContext) extends js.Object {
  val path: String                       = source.path
  val params: js.Dictionary[String]      = source.pathParams.toJSDictionary
  val queryParams: js.Dictionary[String] =
    source.queryParams.entries.toMap.toJSDictionary
  val failure: String | Null =
    source.failure.map(RouterFactories.failureKind).orNull
}

