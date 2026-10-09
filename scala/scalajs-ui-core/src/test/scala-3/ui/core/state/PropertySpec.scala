package ui.core.state

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class PropertySpec extends AnyFlatSpec with Matchers {

  "Property propagation" should "detect cycles longer than a bidirectional pair" in {
    val a = Property(0)
    val b = Property(0)
    val c = Property(0)

    a.observeWithoutInitial(value => b.setAlways(value))
    b.observeWithoutInitial(value => c.setAlways(value))
    c.observeWithoutInitial(value => a.setAlways(value))

    val error = intercept[IllegalStateException](a.set(1))

    error.getMessage should include("Property propagation cycle detected")
  }

  it should "allow two observers to update the same downstream property" in {
    val source = Property(0)
    val target = Property(0)

    source.observeWithoutInitial(value => target.setAlways(value))
    source.observeWithoutInitial(value => target.setAlways(value + 1))

    noException should be thrownBy source.set(1)
    target.get shouldBe 2
  }

  it should "skip observers disposed earlier in the same notification" in {
    val source = Property(0)
    val observed = mutable.ArrayBuffer.empty[Int]
    var later: Disposable = Disposable.empty
    val first = source.observeWithoutInitial(_ => later.dispose())
    later = source.observeWithoutInitial(value => observed += value)
    val last = source.observeWithoutInitial(value => observed += value * 10)

    source.set(1)
    observed.toSeq shouldBe Seq(10)
    first.dispose()
    later.dispose()
    last.dispose()
  }

  it should "release a subscription whose initial callback fails" in {
    val source = Property(0)
    intercept[IllegalArgumentException] {
      source.observe(_ => throw new IllegalArgumentException("Initial callback failed"))
    }
    noException should be thrownBy source.set(1)
  }

  it should "observe the current and replacement inner properties without an initial value" in {
    val first    = Property(1)
    val second   = Property(10)
    val outer    = Property[ReadOnlyProperty[Int]](first)
    val observed = mutable.ArrayBuffer.empty[Int]

    val subscription = outer.flatMap(identity).observeWithoutInitial(observed += _)
    observed shouldBe empty

    first.set(2)
    observed.toVector shouldBe Vector(2)

    outer.set(second)
    observed.toVector shouldBe Vector(2, 10)
    first.set(3)
    second.set(11)
    observed.toVector shouldBe Vector(2, 10, 11)

    subscription.dispose()
    second.set(12)
    observed.toVector shouldBe Vector(2, 10, 11)
  }
}
