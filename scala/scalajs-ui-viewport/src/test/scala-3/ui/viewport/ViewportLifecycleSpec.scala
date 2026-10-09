package ui.viewport

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, SsrCursor}
import ui.viewport.Viewport.viewport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The lifecycle half of the Viewport contract, next to ViewportStateSpec.
  *
  * ViewportStateSpec covers isolation between two viewports, the refusal to move a registered
  * configuration, and notification stacking. This one covers what happens on the way out:
  * ownership, the z-stack, deferred removal, and dispose — where the old process-wide registries
  * used to leak.
  */
class ViewportLifecycleSpec extends AnyFlatSpec with Matchers {

  "A viewport" should "own the windows added through it" in {
    val fixture = mountViewport()
    val conf    = Viewport.WindowConf("Inspector") {}

    fixture.viewport.addWindow(conf)

    fixture.viewport.windows.toSeq should contain(conf)
    conf.id should startWith("window-")

    Runtime.unmount(fixture.root)
  }

  it should "release its configurations on dispose so they can move to another viewport" in {
    val first = mountViewport()
    val conf  = Viewport.WindowConf("Reusable") {}

    first.viewport.addWindow(conf)
    Runtime.unmount(first.root)

    first.viewport.windows shouldBe empty

    val second = mountViewport()
    noException should be thrownBy second.viewport.addWindow(conf)

    Runtime.unmount(second.root)
  }

  it should "stack windows so the last one touched is the active one" in {
    val fixture = mountViewport()
    val back    = Viewport.WindowConf("Back") {}
    val front   = Viewport.WindowConf("Front") {}

    fixture.viewport.addWindow(back)
    fixture.viewport.addWindow(front)

    fixture.viewport.isActive(front) shouldBe true
    fixture.viewport.isActive(back) shouldBe false

    fixture.viewport.touchWindow(back)

    fixture.viewport.isActive(back) shouldBe true
    fixture.viewport.isActive(front) shouldBe false

    Runtime.unmount(fixture.root)
  }

  it should "hide a closing window immediately and remove it only after the fade" in {
    val fixture = mountViewport()
    val conf    = Viewport.WindowConf("Closing") {}

    fixture.viewport.addWindow(conf)
    fixture.viewport.closeWindow(conf)

    // Still present, already invisible: removal waits for the fade-out timer.
    conf.visible.get shouldBe false
    fixture.viewport.windows.toSeq should contain(conf)

    Runtime.unmount(fixture.root)
  }

  it should "reuse a hidden window and keep a single mounted instance" in {
    val fixture = mountViewport()
    val conf = Viewport.WindowConf("Player", placement = Viewport.WindowPlacement.Centered,
      mobileSheet = false, closeBehavior = Viewport.WindowCloseBehavior.Hide) {}

    fixture.viewport.addWindow(conf)
    fixture.viewport.hideWindow(conf)
    conf.visible.get shouldBe false
    fixture.viewport.showWindow(conf)
    conf.visible.get shouldBe true
    fixture.viewport.windows.toSeq shouldBe Seq(conf)

    Runtime.unmount(fixture.root)
  }

  it should "retain a dragged centered window's position while it remains visible" in {
    val fixture = mountViewport()
    val conf = Viewport.WindowConf("Player", placement = Viewport.WindowPlacement.Centered) {}
    fixture.viewport.addWindow(conf)
    conf.leftPx.set(145)
    conf.topPx.set(185)

    fixture.viewport.showWindow(conf)

    conf.leftPx.get shouldBe 145
    conf.topPx.get shouldBe 185
    Runtime.unmount(fixture.root)
  }

  it should "cancel a pending removal when it is disposed" in {
    val fixture = mountViewport()
    val conf    = Viewport.WindowConf("Closing") {}

    fixture.viewport.addWindow(conf)
    fixture.viewport.closeWindow(conf)

    // The scheduled removal must not run against a disposed viewport.
    noException should be thrownBy Runtime.unmount(fixture.root)
    fixture.viewport.windows shouldBe empty
  }

  "Centered placement" should "center a window within the visible area" in {
    val area = Viewport.VisibleArea(30, 100, 800, 400)
    area.center(600, horizontal = true) shouldBe 130
    area.center(200, horizontal = false) shouldBe 200
    area.center(1000, horizontal = true) shouldBe 38
    area.clamp(600, 600, horizontal = true) shouldBe 222
    area.clamp(15, 200, horizontal = false) shouldBe 108
  }

  "Notifications" should "be owned, closable and cleared on dispose" in {
    val fixture = mountViewport()

    val conf = fixture.viewport.notifyProperty(
      ui.core.state.Property("Saved"),
      Viewport.NotificationKind.Info,
      durationMs = 3000
    )

    fixture.viewport.notifications.toSeq should contain(conf)
    conf.message.get shouldBe "Saved"
    conf.visible.get shouldBe true

    fixture.viewport.closeNotification(conf)
    conf.visible.get shouldBe false

    Runtime.unmount(fixture.root)
    fixture.viewport.notifications shouldBe empty
  }

  "Overlays" should "be removed by id and cleared on dispose" in {
    val fixture = mountViewport()
    val conf    = new Viewport.OverlayConf(
      anchor = None,
      body = _ ?=> _ ?=> (),
      widthPx = None,
      effectiveWidthProperty = ui.core.state.Property(0.0)
    )

    fixture.viewport.addOverlay(conf)
    fixture.viewport.overlays.toSeq should contain(conf)

    fixture.viewport.closeOverlayById(conf.id)
    fixture.viewport.overlays shouldBe empty

    Runtime.unmount(fixture.root)
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

