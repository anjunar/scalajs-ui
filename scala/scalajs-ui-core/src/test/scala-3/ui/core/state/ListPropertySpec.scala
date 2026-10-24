package ui.core.state

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class ListPropertySpec extends AnyFlatSpec with Matchers {

  "ListProperty" should "isolate its state from the constructor array" in {
    val source   = js.Array("first")
    val property = ListProperty(source)

    source.push("external")

    property.toSeq shouldBe Seq("first")
  }

  it should "return snapshots that cannot mutate the property without notification" in {
    val property      = ListProperty(js.Array("first"))
    var notifications = 0
    property.observeWithoutInitial(_ => notifications += 1)

    val snapshot = property.get
    snapshot.push("external")

    property.toSeq shouldBe Seq("first")
    notifications shouldBe 0

    property.addOne("second")

    property.toSeq shouldBe Seq("first", "second")
    notifications shouldBe 1
  }

  it should "skip listeners disposed earlier in the same notification" in {
    val property          = ListProperty(js.Array("first"))
    val observed          = mutable.ArrayBuffer.empty[Int]
    var later: Disposable = Disposable.empty
    val first             = property.observeWithoutInitial(_ => later.dispose())
    later = property.observeWithoutInitial(items => observed += items.length)
    val last = property.observeWithoutInitial(items => observed += items.length * 10)

    property.addOne("second")
    observed.toSeq shouldBe Seq(20)
    first.dispose()
    later.dispose()
    last.dispose()
  }

  it should "skip change listeners disposed earlier in the same notification" in {
    val property          = ListProperty(js.Array("first"))
    val observed          = mutable.ArrayBuffer.empty[String]
    var later: Disposable = Disposable.empty
    property.observeChanges(_ => later.dispose())
    later = property.observeChanges(change => observed += s"later ${change.getClass.getSimpleName}")
    property.observeChanges(change => observed += s"last ${change.getClass.getSimpleName}")

    property.addOne("second")
    observed.toSeq shouldBe Seq("last Add")
  }

  it should "release a subscription whose initial callback fails" in {
    val property = ListProperty(js.Array("first"))
    intercept[IllegalArgumentException] {
      property.observe(_ => throw new IllegalArgumentException("Initial callback failed"))
    }
    noException should be thrownBy property.addOne("second")
  }

  it should "reset to its default elements and notify only when dirty" in {
    val property      = ListProperty(js.Array("first"))
    var notifications = 0
    property.observeWithoutInitial(_ => notifications += 1)

    property.reset()
    notifications shouldBe 0

    property.addOne("second")
    property.isDirty shouldBe true
    property.reset()
    property.toSeq shouldBe Seq("first")
    property.isDirty shouldBe false
    notifications shouldBe 2

    property.setDefaultValue(js.Array("a", "b"))
    property.isDirty shouldBe true
    property.reset()
    property.toSeq shouldBe Seq("a", "b")
    notifications shouldBe 3
  }
}
