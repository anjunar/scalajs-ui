package ui.core.statement

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, HostMutationGuard, HostWriteBlocked, SsrCursor}
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js
import ui.core.layout.TextComponent

/** Who owns a container's children list?
  *
  * Before P4-3 there were two answers. `Runtime.mountWithCursor` added the child, and
  * `Foreach.mountAt` then rebuilt the list with `syncChildOrder()` from its own bookkeeping. This
  * went unnoticed while only Foreach mounted children -- everything else vanished at
  * reconciliation.
  *
  * Now only Runtime writes: at the supplied position during mount and removes during unmount.
  */
class ForeachChildOwnershipSpec extends AnyFlatSpec with Matchers {

  "Foreach" should "keep children in list order" in {
    val items = ListProperty[String](js.Array("a", "b", "c"))
    val root  = Runtime.mount(new OwnershipRoot(items), new SsrCursor())

    childTexts(root) shouldBe Seq("a", "b", "c")
  }

  it should "keep list order when inserting in the middle" in {
    val items = ListProperty[String](js.Array("a", "c"))
    val root  = Runtime.mount(new OwnershipRoot(items), new SsrCursor())

    items.insert(1, "b")

    childTexts(root) shouldBe Seq("a", "b", "c")
  }

  it should "keep list order when removing" in {
    val items = ListProperty[String](js.Array("a", "b", "c"))
    val root  = Runtime.mount(new OwnershipRoot(items), new SsrCursor())

    items.remove(1)

    childTexts(root) shouldBe Seq("a", "c")
  }

  it should "not drop a component mounted through another path" in {
    val items   = ListProperty[String](js.Array("a", "b"))
    val cursor  = new SsrCursor()
    val root    = Runtime.mount(new OwnershipRoot(items), cursor)
    val foreach = root.children.head

    val stranger = new StrangerComponent
    Runtime.mount(stranger, foreach._contentCursor, Some(foreach))

    foreach.children should contain(stranger)

    // It remains when the list changes afterwards. Previously syncChildOrder() removed everything
    // not originating from Foreach's own bookkeeping.
    items.addOne("c")

    foreach.children should contain(stranger)
  }

  it should "leave its bookkeeping untouched when a guarded removal is refused" in {
    val items   = ListProperty[String](js.Array("a", "b", "c"))
    val cursor  = new SsrCursor()
    val root    = Runtime.mount(new OwnershipRoot(items), cursor)
    val foreach = root.children.head
    val lease   = HostMutationGuard.protect(foreach.children.head.children.head.host)
    try {
      intercept[HostWriteBlocked](items.remove(0))
      foreach.children.size shouldBe 3
      childTexts(root) shouldBe Seq("a", "b", "c")
      htmlTexts(cursor) shouldBe Seq("a", "b", "c")
    } finally lease.dispose()

    // The list already dropped "a" while Foreach still shows it. Later structural changes must
    // still place a component at the same position logically and physically: before, the refused
    // item had already left Foreach's own bookkeeping, so this insert landed before "b" in the
    // children list but before "c" in the host tree.
    items.insert(1, "x")

    childTexts(root) shouldBe Seq("a", "x", "b", "c")
    htmlTexts(cursor) shouldBe Seq("a", "x", "b", "c")
  }

  private def htmlTexts(cursor: SsrCursor): Seq[String] =
    "<div>([^<]*)</div>".r.findAllMatchIn(cursor.collectHtml()).map(_.group(1)).toSeq

  private def childTexts(root: AbstractComponent): Seq[String] =
    root.children.head.children.map { item =>
      item.children.headOption
        .flatMap(_.children.headOption)
        .collect { case t: TextComponent => t.getText }
        .getOrElse("")
    }.toSeq
}
