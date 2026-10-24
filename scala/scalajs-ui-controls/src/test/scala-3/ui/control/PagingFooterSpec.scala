package ui.control

import ui.control.virtualized.{CollectionDisplayMode, FixedRowGeometry, ItemGeometry, VirtualizedCollection}
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.context.UrlScope
import ui.core.dsl.DslLayer
import ui.core.render.Cursor
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

/** The shared footer pager under a browser host.
  *
  * Its SSR shape is covered per control. What only a browser mount shows is whether the links stay
  * usable once hydration has completed: an anchor without href has no activation behavior, so a
  * pager that drops its href after hydration cannot be operated from the keyboard.
  */
class PagingFooterSpec extends AnyFlatSpec with Matchers {

  "The paging footer" should "keep an enabled link activatable after hydration" in {
    val hosts  = mutable.ArrayBuffer.empty[TabsTestHostElement]
    val cursor = new TabsEventCursor(hosts += _, browser = true, deferredHydration = true)
    var probe: PagingFooterProbe = null

    val root = Runtime.mount(
      new AbstractComponent {
        override val tagName: String               = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          UrlScope.provide(UrlScope(() => "/items") { (_, _) => () })
          probe = DslLayer.child(new PagingFooterProbe(itemCount = 15)) {}
        }
      },
      cursor
    )

    val links    = hosts.filter(_.tagName == "a")
    val previous = links(0)
    val next     = links(1)

    // Hydration claims the server's links unchanged.
    previous.attribute("href") shouldBe None
    previous.attribute("aria-disabled") shouldBe Some("true")
    previous.attribute("tabindex") shouldBe Some("-1")
    next.attribute("href") shouldBe Some("/items?probe.offset=5&probe.limit=5")
    next.attribute("aria-disabled") shouldBe Some("false")

    cursor.completeHydration()

    // The enabled link keeps its href: Enter activates it natively, and the click handler pages in
    // place instead of navigating.
    next.attribute("href") shouldBe Some("/items?probe.offset=5&probe.limit=5")
    next.attribute("aria-disabled") shouldBe Some("false")
    previous.attribute("href") shouldBe None
    previous.attribute("aria-disabled") shouldBe Some("true")

    next.fire("click") shouldBe true
    probe.pageIndexProperty.get shouldBe 1
    previous.attribute("href") shouldBe Some("/items?probe.offset=0&probe.limit=5")
    previous.attribute("aria-disabled") shouldBe Some("false")

    // role="button" promises Space; Enter stays with the link's own activation.
    next.fire("keydown", js.Dynamic.literal(key = "Enter")) shouldBe false
    next.fire("keydown", js.Dynamic.literal(key = " ", ctrlKey = true)) shouldBe false
    probe.pageIndexProperty.get shouldBe 1
    next.fire("keydown", js.Dynamic.literal(key = " ")) shouldBe true
    probe.pageIndexProperty.get shouldBe 2

    // The last page disables the link: no href, and the state the stylesheets key on.
    next.attribute("href") shouldBe None
    next.attribute("aria-disabled") shouldBe Some("true")
    next.attribute("tabindex") shouldBe Some("-1")
    next.fire("click") shouldBe true
    probe.pageIndexProperty.get shouldBe 2

    Runtime.unmount(root)
    next.hasListener("click") shouldBe false
    next.hasListener("keydown") shouldBe false
  }
}

/** A VirtualizedCollection reduced to the footer pager, with a page of five local items. */
private final class PagingFooterProbe(itemCount: Int)
    extends VirtualizedCollection[String](
      ListProperty(js.Array((0 until itemCount).map(_.toString)*))
    ) {

  override val tagName: String = "div"

  override protected val geometry: ItemGeometry =
    new FixedRowGeometry(rowHeight = () => 20.0, headerHeightValue = () => 0.0, overscanRows = 0)

  override protected def renderableCount: Int     = dataSource.totalLength
  override protected def recomputeVisible(): Unit = ()
  override protected def handleLocalItemsChange(change: ListProperty.Change[String]): Unit = ()
  override protected def pagingUrlKey: String                                              = "probe"

  // Needs window.requestAnimationFrame, which the test runtime does not have.
  override protected def scheduleViewportMeasure(): Unit = ()

  override def compose(cursor: Cursor): Unit = {
    browserRendering = cursor.isBrowser
    hydrating = cursor.isHydrating
    pageSizeProperty.set(5)
    configureDisplayMode(CollectionDisplayMode.Paging)
    addDisposable(pageIndexProperty.observeWithoutInitial(_ => refreshItemState()))
    installItemObservers()

    DslLayer.render(this, cursor) {
      renderPagingFooter("ui-probe")
    }
  }
}
