package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent.text
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future, Promise}
class RouterBoundaryAsyncSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext =
    ExecutionContext.parasitic

  "An asynchronous loader failure" should "forward to the error route and carry its status" in {
    val pending = Promise[AbstractComponent]()
    val router  =
      new Router(
        Seq(
          Route.view("/broken")(_ => pending.future),
          Route.error("/500", status = 500) { context =>
            Future.successful(component(s"async failure: ${context.path}"))
          }
        ),
        "/broken",
        RouterConfig(onFailure = _ => Some("/500"), renderErrorsOnServer = true)
      )

    val rendered =
      Runtime.renderToStringAsync(cursor => Runtime.mount(router, cursor))

    pending.failure(new RuntimeException("failed later"))

    rendered.map { html =>
      html should include("async failure: /broken")
      router.responseStatus.get shouldBe 500
    }
  }

  private def component(value: String): AbstractComponent =
    Route.component {
      text(value) {}
    }
}
