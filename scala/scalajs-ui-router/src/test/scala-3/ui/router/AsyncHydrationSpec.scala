package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.TextComponent.text
import ui.core.render.{CommentNode, Cursor, HostElement, HostNode, TextNode, VirtualRange}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/** Routes are asynchronous -- all of them, without exception.
  *
  * Before P4-1 the router threw during hydration whenever a loader did not finish synchronously.
  * This went unnoticed only because every demo route used Future.successful; the first real data
  * route would have broken hydration. SSR was therefore effectively usable only for static pages.
  *
  * The router now adopts the server-rendered tree without validation and replaces it when the
  * loader completes. The cost is a second load -- deliberately chosen instead of an SSR data cache.
  */
class AsyncHydrationSpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  "Hydration" should "adopt the server-rendered tree while the loader is still running" in {
    val pending = Promise[AbstractComponent]()
    val cursor  = new HydrationTestCursor

    Runtime.mount(routerFor(pending.future), cursor)

    // The route range was adopted rather than rebuilt.
    cursor.adopted should contain("RoutedComponent")
    cursor.claimed should not contain "RoutedComponent"
  }

  it should "not throw when the loader is unresolved" in {
    val pending = Promise[AbstractComponent]()

    noException should be thrownBy {
      Runtime.mount(routerFor(pending.future), new HydrationTestCursor)
    }
  }

  it should "replace the adopted tree once the loader delivers" in {
    val pending = Promise[AbstractComponent]()
    val cursor  = new HydrationTestCursor

    Runtime.mount(routerFor(pending.future), cursor)
    val adoptedBefore = cursor.adopted.count(_ == "RoutedComponent")

    pending.success(Route.component { text("geladen") {} })

    // The real tree is now claimed normally rather than adopted.
    cursor.adopted.count(_ == "RoutedComponent") shouldBe adoptedBefore
    cursor.texts should contain("geladen")
  }

  it should "claim normally when the loader is already resolved" in {
    val cursor = new HydrationTestCursor

    Runtime.mount(routerFor(Future.successful(Route.component { text("sofort") {} })), cursor)

    cursor.claimed should contain("RoutedComponent")
    cursor.adopted should not contain "RoutedComponent"
    cursor.texts should contain("sofort")
  }

  it should "emit synchronous completion after the hydrated route is composed" in {
    val cursor = new HydrationTestCursor
    val router = routerFor(Future.successful(Route.component { text("ready") {} }))
    val events = mutable.ArrayBuffer.empty[String]
    router.onPageLoad(_ => events += "load")
    router.onPageResolved(_ => {
      cursor.texts should contain("ready")
      events += "resolved"
    })
    Runtime.mount(router, cursor)
    events.toSeq shouldBe Seq("load", "resolved")
    router.loading.get shouldBe false
    Runtime.unmount(router)
  }

  it should "keep loading through adoption until the asynchronous route is rendered" in {
    val cursor  = new HydrationTestCursor
    val pending = Promise[AbstractComponent]()
    val router  = routerFor(pending.future)
    val events  = mutable.ArrayBuffer.empty[String]
    router.onPageLoad(_ => events += "load")
    router.onPageResolved(_ => {
      cursor.texts should contain("ready")
      events += "resolved"
    })
    Runtime.mount(router, cursor)
    events.toSeq shouldBe Seq("load")
    router.loading.get shouldBe true
    pending.success(Route.component { text("ready") {} })
    events.toSeq shouldBe Seq("load", "resolved")
    router.loading.get shouldBe false
    Runtime.unmount(router)
  }

  it should "show the error component when an async loader fails" in {
    val pending = Promise[AbstractComponent]()
    val cursor  = new HydrationTestCursor

    Runtime.mount(routerFor(pending.future), cursor)
    pending.failure(new RuntimeException("Laden fehlgeschlagen"))

    cursor.texts should contain("Route could not be loaded")
  }

  it should "hydrate the parent while adopting an unresolved child route" in {
    val pending = Promise[AbstractComponent]()
    val cursor  = new HydrationTestCursor
    val router  =
      new Router(
        Seq(
          Route.view(
            "/parent",
            children = Seq(Route.view("child")(_ => pending.future))
          ) { _ =>
            Future.successful(Route.component {
              text("parent") {}
              Router.routerOutlet()
            })
          }
        ),
        "/parent/child"
      )

    Runtime.mount(router, cursor)

    cursor.texts should contain("parent")
    cursor.claimed.count(_ == "RoutedComponent") shouldBe 1
    cursor.adopted.count(_ == "RoutedComponent") shouldBe 1

    pending.success(Route.component { text("child") {} })

    cursor.texts should contain("child")
    cursor.claimed.count(_ == "RoutedComponent") shouldBe 2
  }

  private def routerFor(loaded: Future[AbstractComponent]): Router =
    new Router(Seq(Route.view("/")(_ => loaded)), "/")
}
