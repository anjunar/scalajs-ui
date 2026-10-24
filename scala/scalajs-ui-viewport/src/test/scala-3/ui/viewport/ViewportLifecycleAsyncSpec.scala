package ui.viewport

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.SsrCursor
import ui.viewport.Viewport.viewport
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.timers.setTimeout

/** The part of the window lifecycle that waits for the fade-out timer: what a close scheduled, and
  * what a re-add in the meantime does to it.
  */
class ViewportLifecycleAsyncSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  "A closed window" should "come back visible when it is added again after its removal" in {
    val fixture = mountViewport()
    val conf    = Viewport.WindowConf("Inspector") {}

    fixture.viewport.addWindow(conf)
    fixture.viewport.closeWindow(conf)

    afterFadeOut().map { _ =>
      fixture.viewport.windows shouldBe empty

      fixture.viewport.addWindow(conf)

      conf.visible.get shouldBe true
      fixture.viewport.windows.toSeq shouldBe Seq(conf)

      Runtime.unmount(fixture.root)
      succeed
    }
  }

  it should "survive the scheduled removal when it is added again during the fade-out" in {
    val fixture = mountViewport()
    val conf    = Viewport.WindowConf("Inspector") {}

    fixture.viewport.addWindow(conf)
    fixture.viewport.closeWindow(conf)
    fixture.viewport.addWindow(conf)

    afterFadeOut().map { _ =>
      fixture.viewport.windows.toSeq shouldBe Seq(conf)
      conf.visible.get shouldBe true

      Runtime.unmount(fixture.root)
      succeed
    }
  }

  private def afterFadeOut(): Future[Unit] = {
    val done = Promise[Unit]()
    setTimeout(Viewport.windowFadeOutMs + 50) { done.success(()) }
    done.future
  }

  private def mountViewport(): Fixture = {
    var mounted: Viewport = null
    val root              = Runtime.mount(
      new ViewportRoot({ mounted = viewport {} }),
      new SsrCursor()
    )
    Fixture(root, mounted)
  }

  private final case class Fixture(root: AbstractComponent, viewport: Viewport)
}
