package ui.core.render

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent
import ui.core.statement.KeyedChildren
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class KeyedReorderSpec extends AnyFlatSpec with Matchers {
  private class Probe {
    var writes = 0
    var failAt = Int.MaxValue
  }
  // Instrument the cursor's insertion host, retaining real SSR nodes and guards.
  private class CountedHost(delegate: HostElement, probe: Probe) extends HostElement {
    export delegate.{
      tagName,
      setAttribute,
      removeAttribute,
      attribute,
      setProperty,
      property,
      setStyle,
      removeStyle,
      style,
      setClassNames,
      insertChild,
      removeChild,
      clearChildren,
      childCount,
      renderHtml
    }
    def insertBefore(child: HostNode, before: Option[HostNode]): Unit = {
      probe.writes += 1
      if (probe.writes == probe.failAt)
        throw new IllegalStateException("injected insertion failure")
      delegate.insertBefore(child, before)
    }
  }
  private class CountedCursor(delegate: Cursor, probe: Probe) extends Cursor {
    override def supportsAnchors                = delegate.supportsAnchors
    override def parentHost                     = delegate.parentHost.map(new CountedHost(_, probe))
    def claimElement(tag: String)               = delegate.claimElement(tag)
    def claimText(initial: String)              = delegate.claimText(initial)
    override def claimComment(text: String)     = delegate.claimComment(text)
    def sub(host: HostElement): Cursor          = new CountedCursor(delegate.sub(host), probe)
    override def before(node: HostNode): Cursor = new CountedCursor(delegate.before(node), probe)
    override def fresh: Cursor                  = new CountedCursor(delegate.fresh, probe)
  }
  private class Box                extends AbstractComponent { val tagName = "div" }
  private class Label(val id: Int) extends AbstractComponent {
    val tagName                                = "p"
    val text                                   = new TextComponent(id.toString)
    override def compose(cursor: Cursor): Unit = Runtime.mount(text, cursor, Some(this))
  }
  private class Fixture(size: Int) {
    val ssr     = new SsrCursor()
    val probe   = new Probe()
    val root    = Runtime.mount(new Box(), new CountedCursor(ssr, probe))
    var created = 0
    val group   = Runtime.mount(
      new KeyedChildren[Int, Int, Label](
        (0 until size).toVector,
        identity,
        id => { created += 1; new Label(id) },
        (_, _) => ()
      ),
      Runtime.contentCursor(root),
      Some(root)
    )
    def physical: Vector[Int] =
      "<p>([0-9]+)</p>".r.findAllMatchIn(ssr.collectHtml()).map(_.group(1).toInt).toVector
    def logical: Vector[Int] = group.children.map(_.asInstanceOf[Label].id).toVector
    def close(): Unit        = Runtime.unmount(root)
  }

  for (size <- Vector(500, 50000)) {
    "A keyed rotation" should s"move one host among $size siblings and preserve ownership" in {
      val f = new Fixture(size)
      try {
        val original = f.group.children.toVector
        val target   = (1 until size).toVector :+ 0
        f.probe.writes = 0
        f.group.setItems(target)
        f.probe.writes shouldBe 1
        f.created shouldBe size
        f.group.children.toVector shouldBe (original.tail :+ original.head)
        f.physical shouldBe target
        original.foreach { child =>
          child.parent shouldBe Some(f.group)
          child.isDisposed shouldBe false
        }
        f.probe.writes = 0
        f.group.setItems((0 until size).toVector)
        f.probe.writes shouldBe 1
        f.group.children.toVector shouldBe original
        f.probe.writes = 0
        f.group.setItems((0 until size).toVector)
        f.probe.writes shouldBe 0
      } finally f.close()
    }
  }

  "Permutation reconciliation" should "match an independent minimal-move oracle" in {
    val f      = new Fixture(24)
    val random = new scala.util.Random(28L)
    try {
      var previous = (0 until 24).toVector
      (0 until 100).foreach { _ =>
        val target = random.shuffle(previous)
        // Quadratic reference LIS, intentionally different from Runtime's binary search.
        val positions = target.map(previous.indexOf)
        val lengths   = Array.fill(positions.size)(1)
        positions.indices.foreach { i =>
          (0 until i).foreach { j =>
            if (positions(j) < positions(i)) lengths(i) = lengths(i).max(lengths(j) + 1)
          }
        }
        f.probe.writes = 0
        f.group.setItems(target)
        f.probe.writes shouldBe (target.size - lengths.max)
        f.logical shouldBe target
        f.physical shouldBe target
        f.created shouldBe target.size
        previous = target
      }
    } finally f.close()
  }

  it should "append new children then place them before retained hosts without remounting" in {
    val f = new Fixture(5000)
    try {
      val retained = f.group.componentFor(1).get
      val removed  = f.group.componentFor(0).get
      val tail     = Runtime.mount(new Label(6000), Runtime.contentCursor(f.root), Some(f.root))
      f.probe.writes = 0
      val target = 5000 +: (1 until 5000).toVector
      f.group.setItems(target)
      f.probe.writes shouldBe 1
      f.created shouldBe 5001
      removed.isDisposed shouldBe true
      f.group.componentFor(1).get should be theSameInstanceAs retained
      f.logical shouldBe target
      f.physical shouldBe (target :+ 6000)
      tail.parent shouldBe Some(f.root)
    } finally f.close()
  }

  it should "reject a guarded permutation before any insertion and allow retry" in {
    val f     = new Fixture(5)
    val lease = HostMutationGuard.protect(f.group.componentFor(1).get.host)
    try {
      f.probe.writes = 0
      intercept[HostWriteBlocked](Runtime.reorderChildren(f.group, f.group.children.reverse))
      f.probe.writes shouldBe 0
      f.logical shouldBe (0 until 5).toVector
      f.physical shouldBe f.logical
    } finally lease.dispose()
    try {
      f.group.setItems((0 until 5).reverse.toVector)
      f.physical shouldBe f.logical
    } finally f.close()
  }

  it should "retain accurate ownership after a backend rejects a later insertion" in {
    val f = new Fixture(5)
    try {
      f.probe.writes = 0
      f.probe.failAt = 2
      intercept[IllegalStateException](f.group.setItems((0 until 5).reverse.toVector))
      f.physical shouldBe f.logical
      f.logical should not be (0 until 5).toVector
      f.group.children.foreach(_.parent shouldBe Some(f.group))
      f.probe.failAt = Int.MaxValue
      f.group.setItems((0 until 5).reverse.toVector)
      f.logical shouldBe (0 until 5).reverse.toVector
      f.physical shouldBe f.logical
      f.created shouldBe 5
    } finally f.close()
  }

  it should "reject missing, duplicate and foreign children without changing either tree" in {
    val f = new Fixture(3)
    try {
      val original = f.group.children.toVector
      val foreign  = Runtime.mount(new Label(4), Runtime.contentCursor(f.root), Some(f.root))
      f.probe.writes = 0
      intercept[IllegalArgumentException](Runtime.reorderChildren(f.group, original.tail))
      intercept[IllegalArgumentException](
        Runtime.reorderChildren(f.group, original.updated(1, original.head))
      )
      intercept[IllegalArgumentException](
        Runtime.reorderChildren(f.group, original.updated(1, foreign))
      )
      f.probe.writes shouldBe 0
      f.group.children.toVector shouldBe original
      f.physical shouldBe Vector(0, 1, 2, 4)
    } finally f.close()
  }
}
