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
