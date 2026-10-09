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

/** Step 5 of JAVASCRIPT_API.md §9: the router facade.
  *
  * The trigger from CLAUDE_REVIEW_3.md §5 was "`scalajs-ui-bridge` gets `dependsOn(uiRouter)`
  * **and** exports (a) a registry entry that mounts a `ui.router.Router` with a route table
  * translated from JS, (b) `router-outlet`, (c) `router-link`". This file is those three, plus the
  * JS <-> Scala translation `ui.router.Router` needs and the deleted `router.ts` only sketched.
  *
  * The hard part is `load`: `ui.router.Route` takes `RouteContext => Future[AbstractComponent]`,
  * where the component is a virtual boundary the router renders. TypeScript writes a
  * `(ctx) => Promise<PageBody>` where `PageBody = () => void` runs the ambient-scope DSL. The TS
  * facade (`npm/scalajs-ui-router/src/router.ts`) already rewrites that into
  * `(ctx) => Promise<(scope) => void>` by wrapping the body in `withScope`; this side turns the
  * resolved `(scope) => void` into an `AbstractComponent` via [[Route.component]].
  */

/** The shape TypeScript hands in for one route. Native: Scala never builds one.
  *
  * `load` returns either a `ScopeBody` (`js.Function1[ScopeHandleBridge, Unit]`) for a synchronous
  * loader, or a `js.Promise` of one for an asynchronous loader -- [[RouterFactories.buildRoute]]
  * branches on `js.typeOf`. A synchronous loader takes the same one-pass path as
  * `Future.successful` on the Scala side, which is the path that hydrates cleanly.
  */
@js.native
private[bridge] trait RouteFacade extends js.Object {
  val path: String                                                          = js.native
  val load: js.Function1[RouteContextHandle, js.Any]                        = js.native
  val children: js.UndefOr[js.Array[RouteFacade]]                           = js.native
  val constraints: js.UndefOr[js.Dictionary[js.Function1[String, Boolean]]] = js.native
  val status: js.UndefOr[Int]                                               = js.native
}
