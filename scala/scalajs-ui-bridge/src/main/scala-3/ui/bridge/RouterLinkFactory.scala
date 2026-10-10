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

/** `router-link` -- a navigating anchor. `dsl.ts`/`router.ts` fold `{ href, label, ...options }`
  * into one options object; this factory takes it back apart, exactly like [[ButtonFactory]].
  */
private[bridge] object RouterLinkFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val href        = options.getOrElse("href", "").asInstanceOf[String]
    val activeClass = options.get("activeClass").map(_.asInstanceOf[String]).getOrElse("active")
    val label       = options.get("label")

    RouterLink.routerLink(href, activeClass) {
      val link       = summon[Anchor]
      val linkCursor = summon[Cursor]

      // `Cursor` is already a given from the context function; only the component is missing --
      // the body runs as the anchor, so `child()` and friends mount under it.
      given AbstractComponent = link

      label.foreach { value =>
        DslLayer.child(TextComponent.bind(ReactiveBridge.asProperty[String](value))) {}
      }

      body(new ComponentHandleBridge(link), new ScopeHandleBridge(link, linkCursor))
    }
  }
}
