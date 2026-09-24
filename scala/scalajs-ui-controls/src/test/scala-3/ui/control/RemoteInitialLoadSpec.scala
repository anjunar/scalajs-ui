package ui.control

import ui.control.virtualized.{FixedRowGeometry, ItemGeometry, VirtualizedCollection}
import ui.core.remote.{RemoteListProperty, RemoteLoader, RemotePage}
import ui.core.render.Cursor
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Promise}

class RemoteInitialLoadSpec extends AnyFlatSpec with Matchers {

  "A virtualized collection" should "keep a known empty result when mounted or hydrated" in {
    for (hydrating <- Seq(false, true)) {
      val fixture = new Fixture(Some(0))
      fixture.attach(hydrating = hydrating)

      fixture.requests shouldBe 0
      fixture.source.loadingProperty.get shouldBe false
      fixture.control.dispose()
    }
  }

  it should "load an uninitialized source whose total is unknown" in {
    val fixture = new Fixture(None)
    fixture.attach()

    fixture.requests shouldBe 1
    fixture.source.loadingProperty.get shouldBe true
    fixture.control.dispose()
  }

  it should "load missing items when the total is known to be positive" in {
    val fixture = new Fixture(Some(3))
    fixture.attach()

    fixture.requests shouldBe 1
    fixture.source.loadingProperty.get shouldBe true
    fixture.control.dispose()
  }

  it should "allow an explicit reload after an empty result" in {
    val fixture = new Fixture(Some(0))
    fixture.attach()
    fixture.requests shouldBe 0
    fixture.source.reload()
    fixture.response.success(RemotePage(items = Seq("New post"), totalCount = Some(1)))

    fixture.requests shouldBe 1
    fixture.source.itemAt(0) shouldBe Some("New post")
    fixture.source.loadingProperty.get shouldBe false
    fixture.control.dispose()
  }

  private class Fixture(total: Option[Int]) {
    var requests = 0
    val response = Promise[RemotePage[String, Unit]]()
    val source = RemoteListProperty[String, Unit](
      loader = RemoteLoader { _ =>
        requests += 1
        response.future
      },
      initialQuery = (),
      executionContext = ExecutionContext.parasitic
    )
    source.totalCountProperty.set(total)
    val control = new InitialLoadProbe(source)

    def attach(hydrating: Boolean = false): Unit = control.attach(hydrating)
  }
}

/** Exercises the shared data-source lifecycle without requiring DOM layout. */
private final class InitialLoadProbe(source: RemoteListProperty[String, Unit])
    extends VirtualizedCollection[String](source) {
  override val tagName: String = "div"
  override protected val geometry: ItemGeometry =
    new FixedRowGeometry(rowHeight = () => 20.0, headerHeightValue = () => 0.0, overscanRows = 0)
  override protected def renderableCount: Int = source.totalLength
  override protected def recomputeVisible(): Unit = ()
  override protected def handleLocalItemsChange(change: ListProperty.Change[String]): Unit = ()
  override def compose(cursor: Cursor): Unit = ()

  def attach(initialHydration: Boolean): Unit = {
    browserRendering = true
    hydrating = initialHydration
    installItemObservers()
  }
}
