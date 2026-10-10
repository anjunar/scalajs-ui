package ui.router

import ui.core.component.Runtime
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import ui.core.component.AbstractComponent

class NestedRouteAsyncSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext =
    ExecutionContext.parasitic

  "Asynchronous nested routes" should "join the SSR render before HTML is collected" in {
    val child  = Promise[AbstractComponent]()
    val routes =
      Seq(
        Route.view(
          "/parent",
          children = Seq(Route.view("child")(_ => child.future))
        ) { _ =>
          Future.successful(Route.component {
            text("parent") {}
            Router.routerOutlet()
          })
        }
      )

    val rendered = Runtime.renderToStringAsync { cursor =>
      Runtime.mount(new Router(routes, "/parent/child"), cursor)
    }

    child.success(Route.component { text("async-child") {} })

    rendered.map { html =>
      html should include("parent")
      html should include("async-child")
      html should not include "Loading..."
    }
  }
}
