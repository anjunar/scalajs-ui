package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent.text
import ui.core.render.SsrCursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}

class RouterLoadingSpec extends AnyFlatSpec with Matchers {
  private given ExecutionContext = ExecutionContext.parasitic

  private def page(label: String): AbstractComponent =
    Route.component { text(label) {} }

  "Router loading" should "retain the current page and emit completion only after replacement" in {
    val pending = Promise[AbstractComponent]()
    val router  = new Router(
      Seq(
        Route.view("/")(_ => Future.successful(page("home"))),
        Route.view("/slow")(_ => pending.future)
      ),
      "/"
    )
    val cursor      = new SsrCursor()
    val events      = ArrayBuffer.empty[String]
    val completions = ArrayBuffer.empty[(String, Boolean, String)]
    router.onPageLoad(state => events += s"load:${state.path}")
    router.onPageResolved(state => {
      events += s"resolved:${state.path}"
      completions += ((state.path, router.loading.get, cursor.collectHtml()))
    })
    Runtime.mount(router, cursor)
    try {
      events.toSeq shouldBe Seq("load:/", "resolved:/")
      router.navigate("/slow")
      router.loading.get shouldBe true
      cursor.collectHtml() should include("home")
      cursor.collectHtml() should not include "Loading"
      events.last shouldBe "load:/slow"

      pending.success(page("ready"))
      cursor.collectHtml() should include("ready")
      cursor.collectHtml() should not include "home"
      events.toSeq shouldBe Seq("load:/", "resolved:/", "load:/slow", "resolved:/slow")
      completions.map(_._2).toSeq shouldBe Seq(false, false)
      completions.head._3 should include("home")
      completions.last._3 should include("ready")
    } finally Runtime.unmount(router)
  }

  it should "wait for nested outlets and emit once for the navigation" in {
    val child  = Promise[AbstractComponent]()
    val router = new Router(
      Seq(
        Route.view(
          "/parent",
          children = Seq(
            Route.view("child")(_ => child.future)
          )
        )(_ =>
          Future.successful(Route.component {
            text("parent") {}
            Router.routerOutlet()
          })
        )
      ),
      "/parent/child"
    )
    val cursor   = new SsrCursor()
    val resolved = ArrayBuffer.empty[String]
    router.onPageResolved(state => resolved += state.path)
    Runtime.mount(router, cursor)
    try {
      router.loading.get shouldBe true
      resolved shouldBe empty
      child.success(page("child"))
      router.loading.get shouldBe false
      resolved.toSeq shouldBe Seq("/parent/child")
      cursor.collectHtml() should include("child")
    } finally Runtime.unmount(router)
  }

  it should "ignore superseded successes and failures while the latest page is pending" in {
    val first  = Promise[AbstractComponent]()
    val second = Promise[AbstractComponent]()
    val latest = Promise[AbstractComponent]()
    val router = new Router(
      Seq(
        Route.view("/")(_ => Future.successful(page("home"))),
        Route.view("/first")(_ => first.future),
        Route.view("/second")(_ => second.future),
        Route.view("/latest")(_ => latest.future)
      ),
      "/"
    )
    val cursor   = new SsrCursor()
    val resolved = ArrayBuffer.empty[String]
    router.onPageResolved(state => resolved += state.path)
    Runtime.mount(router, cursor)
    try {
      router.navigate("/first")
      router.navigate("/second")
      router.navigate("/latest")
      first.success(page("obsolete"))
      second.failure(new RuntimeException("obsolete failure"))
      router.loading.get shouldBe true
      cursor.collectHtml() should include("home")
      resolved.toSeq shouldBe Seq("/")
      latest.success(page("latest"))
      resolved.toSeq shouldBe Seq("/", "/latest")
      router.loading.get shouldBe false
    } finally Runtime.unmount(router)
  }

  it should "finish an error forward only after its asynchronous boundary renders" in {
    val failed   = Promise[AbstractComponent]()
    val boundary = Promise[AbstractComponent]()
    val router   = new Router(
      Seq(
        Route.view("/")(_ => Future.successful(page("home"))),
        Route.view("/broken")(_ => failed.future),
        Route.error("/500", 500)(_ => boundary.future)
      ),
      "/",
      RouterConfig(onFailure = _ => Some("/500"), renderErrorsOnServer = true)
    )
    val cursor = new SsrCursor()
    val events = ArrayBuffer.empty[String]
    router.onPageLoad(state => events += s"load:${state.path}")
    router.onPageResolved(state => events += s"resolved:${state.path}")
    Runtime.mount(router, cursor)
    try {
      router.navigate("/broken")
      failed.failure(new RuntimeException("failed"))
      router.loading.get shouldBe true
      cursor.collectHtml() should include("home")
      boundary.success(page("error boundary"))
      router.loading.get shouldBe false
      cursor.collectHtml() should include("error boundary")
      events.toSeq shouldBe Seq("load:/", "resolved:/", "load:/broken", "resolved:/broken")
    } finally Runtime.unmount(router)
  }

  it should "allow listener removal and ignore pending work after disposal" in {
    val pending = Promise[AbstractComponent]()
    val router  = new Router(Seq(Route.view("/")(_ => pending.future)), "/")
    val cursor  = new SsrCursor()
    val events  = ArrayBuffer.empty[String]
    val removed = router.onPageLoad(_ => events += "removed")
    removed.dispose()
    router.onPageLoad(_ => events += "load")
    router.onPageResolved(_ => events += "resolved")
    Runtime.mount(router, cursor)
    Runtime.unmount(router)
    pending.success(page("late"))
    events.toSeq shouldBe Seq("load")
    router.loading.get shouldBe false
    cursor.collectHtml() should not include "late"
  }

  it should "finish at the fallback if an error outlet fails while another is still pending" in {
    val pending = Promise[AbstractComponent]()
    val failed  = Promise[AbstractComponent]()
    var calls   = 0
    val router  = new Router(
      Seq(
        Route.view("/broken")(_ => Future.failed(new RuntimeException("broken"))),
        Route.error(
          "/500",
          500,
          children = Seq(Route.error("child", 500)(_ => {
            calls += 1
            if (calls == 1) pending.future
            else failed.future
          }))
        )(_ =>
          Future.successful(Route.component {
            Router.routerOutlet()
            Router.routerOutlet()
          })
        )
      ),
      "/broken",
      RouterConfig(onFailure = _ => Some("/500/child"), renderErrorsOnServer = true)
    )
    val cursor   = new SsrCursor()
    val resolved = ArrayBuffer.empty[String]
    router.onPageResolved(state => resolved += state.path)
    Runtime.mount(router, cursor)
    try {
      router.loading.get shouldBe true
      failed.failure(new RuntimeException("boundary failed"))
      router.loading.get shouldBe false
      resolved.toSeq shouldBe Seq("/broken")
      cursor.collectHtml() should include("Route could not be loaded")
      pending.success(page("obsolete boundary"))
      resolved.toSeq shouldBe Seq("/broken")
      cursor.collectHtml() should not include "obsolete boundary"
    } finally Runtime.unmount(router)
  }

  it should "allow a load listener to redirect without starting the superseded loader" in {
    var obsoleteLoads = 0
    val router        = new Router(
      Seq(
        Route.view("/")(_ => Future.successful(page("home"))),
        Route.view("/obsolete")(_ => {
          obsoleteLoads += 1
          Future.successful(page("obsolete"))
        }),
        Route.view("/latest")(_ => Future.successful(page("latest")))
      ),
      "/"
    )
    val cursor = new SsrCursor()
    val events = ArrayBuffer.empty[String]
    router.onPageLoad(state => {
      if (state.path == "/obsolete") router.navigate("/latest")
    })
    router.onPageLoad(state => events += s"load:${state.path}")
    router.onPageResolved(state => events += s"resolved:${state.path}")
    Runtime.mount(router, cursor)
    try {
      router.navigate("/obsolete")
      obsoleteLoads shouldBe 0
      events.toSeq shouldBe Seq("load:/", "resolved:/", "load:/latest", "resolved:/latest")
      cursor.collectHtml() should include("latest")
    } finally Runtime.unmount(router)
  }
}
