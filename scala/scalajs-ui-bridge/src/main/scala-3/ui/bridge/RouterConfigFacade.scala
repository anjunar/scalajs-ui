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

/** Mirrors `router.ts`'s `RouterConfig`. Native, same reason. */
@js.native
private[bridge] trait RouterConfigFacade extends js.Object {
  val basePath: js.UndefOr[String]                              = js.native
  val initialUrl: js.UndefOr[String]                            = js.native
  val onFailure: js.UndefOr[js.Function1[js.Object, js.Any]]    = js.native
  val renderErrorsOnServer: js.UndefOr[Boolean]                 = js.native
  val onPageLoad: js.UndefOr[js.Function1[js.Object, Unit]]     = js.native
  val onPageResolved: js.UndefOr[js.Function1[js.Object, Unit]] = js.native
}
