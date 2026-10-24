package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent.text
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

/** What the router writes into the session history, and what it leaves to the browser.
  *
  * A navigation creates or replaces an entry and scrolls once the page is there. Back/Forward is
  * the other direction: the browser has already moved to an entry it owns, fragment and scroll
  * position included, and the router only adopts it.
  */
class RouterHistorySpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  private def page(label: String): AbstractComponent =
    Route.component { text(label) {} }

  private val routes =
    Seq(
      Route.view("/")(_ => Future.successful(page("home"))),
      Route.view("/docs")(_ => Future.successful(page("docs")))
    )

  private def mounted(history: TestRouterHistory): Router = {
    val router = new Router(routes, history.url, RouterConfig(), history)
    Runtime.mount(router, new BrowserTestCursor(history))
    router
  }

  "A navigation" should "push an entry and scroll to its fragment" in {
    val history = new TestRouterHistory("/")
    val router  = mounted(history)

    router.navigate("/docs#usage")

    router.state.get.url shouldBe "/docs#usage"
    router.state.get.fragment shouldBe Some("usage")
    history.writes.toSeq shouldBe Seq("push /docs#usage")
    history.scrolls.toSeq shouldBe Seq(Some("usage"))

    Runtime.unmount(router)
  }

  it should "replace the entry instead of stacking a duplicate when nothing changes" in {
    val history = new TestRouterHistory("/")
    val router  = mounted(history)

    router.navigate("/docs")
    router.navigate("/docs")

    history.writes.toSeq shouldBe Seq("push /docs", "replace /docs")

    Runtime.unmount(router)
  }

  "Back or Forward" should "adopt the browser's entry with its fragment and write nothing" in {
    val history = new TestRouterHistory("/#intro")
    val router  = mounted(history)

    router.state.get.hash shouldBe "#intro"
    router.navigate("/docs#usage")
    history.writes.clear()
    history.scrolls.clear()

    history.travel("/#intro")

    router.state.get.url shouldBe "/#intro"
    router.state.get.hash shouldBe "#intro"
    router.state.get.path shouldBe "/"
    // The entry stays as the browser has it, and the browser restores its own scroll position.
    history.writes shouldBe empty
    history.scrolls shouldBe empty

    Runtime.unmount(router)
  }

  it should "stop being answered once the router is unmounted" in {
    val history = new TestRouterHistory("/")
    val router  = mounted(history)

    history.listening shouldBe true

    Runtime.unmount(router)

    history.listening shouldBe false
  }
}
