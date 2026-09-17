package ui.control.table

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import TableColumnLayout.*
import ColumnResizePolicy.*

class TableColumnLayoutSpec extends AnyFlatSpec with Matchers {
  private def col(
      width: Double = 100,
      min: Double = 40,
      max: Double = 300,
      resizable: Boolean = true
  ): Column = Column(min, width, max, resizable)

  "Column layout" should "respect bounds and leave unconstrained preferred widths alone" in {
    layout(Vector(col(10), col(500)), 800, Unconstrained) shouldBe Vector(40, 300)
    layout(Vector(col(), col()), 500, FlexLastColumn) shouldBe Vector(250, 250)
  }

  it should "redistribute until all space or all capacity is consumed" in {
    layout(Vector(col(max = 110), col(max = 150), col()), 500, AllColumns) shouldBe Vector(
      110,
      150,
      240
    )
    layout(Vector(col(), col()), 10, AllColumns) shouldBe Vector(40, 40)
    layout(Vector(col(), col()), 1000, AllColumns) shouldBe Vector(300, 300)
  }

  it should "keep non-resizable columns fixed and sanitize invalid constraints" in {
    layout(Vector(col(resizable = false), col()), 400, NextColumn) shouldBe Vector(100, 300)
    Column(Double.NaN, Double.NaN, Double.NaN, true).initial shouldBe 160
    Column(80, 10, 30, true).initial shouldBe 80
    Column(-30, 10, 30, true).minimum shouldBe 0
  }

  "Column resizing" should "apply unconstrained deltas without changing neighbors" in {
    val columns = Vector.fill(3)(col())
    resize(columns, Vector(100, 100, 100), 1, 500, Unconstrained) shouldBe Vector(100, 300, 100)
    resize(columns, Vector(100, 100, 100), 1, -500, Unconstrained) shouldBe Vector(100, 40, 100)
  }

  it should "compensate only the requested next or last column" in {
    val columns = Vector.fill(4)(col())
    val widths  = Vector.fill(4)(100.0)
    resize(columns, widths, 0, 100, NextColumn) shouldBe Vector(160, 40, 100, 100)
    resize(columns, widths, 0, 100, LastColumn) shouldBe Vector(160, 100, 100, 40)
    resize(columns, widths, 3, 10, LastColumn) shouldBe widths
  }

  it should "continue across saturated columns in flex direction" in {
    val columns = Vector.fill(4)(col())
    val widths  = Vector.fill(4)(100.0)
    resize(columns, widths, 0, 100, FlexNextColumn) shouldBe Vector(200, 40, 60, 100)
    resize(columns, widths, 0, 100, FlexLastColumn) shouldBe Vector(200, 100, 60, 40)
    resize(columns, widths, 0, -50, FlexLastColumn) shouldBe Vector(50, 100, 100, 150)
  }

  it should "proportionally compensate all or subsequent columns" in {
    val columns = Vector(col(120), col(100), col(240))
    val widths  = Vector(120.0, 100.0, 240.0)
    resize(columns, widths, 1, 60, AllColumns) shouldBe Vector(100, 160, 200)
    resize(columns, widths, 1, 60, SubsequentColumns) shouldBe Vector(120, 160, 180)
  }

  it should "ignore invalid indices, deltas and locked columns" in {
    val columns = Vector(col(resizable = false), col())
    val widths  = Vector(100.0, 100.0)
    resize(columns, widths, 0, 10, Unconstrained) shouldBe widths
    resize(columns, widths, 1, 10, AllColumns) shouldBe widths
    resize(columns, widths, -1, 10, Unconstrained) shouldBe widths
    resize(columns, widths, 2, 10, Unconstrained) shouldBe widths
    resize(columns, widths, 1, Double.NaN, Unconstrained) shouldBe widths
  }

  "Group resizing" should "share a delta across every index proportionally to its width" in {
    val columns = Vector(col(), col(), col())
    val widths  = Vector(100.0, 100.0, 100.0)
    // Unconstrained: no neighbor compensation, so both group members reach their full request.
    resizeGroup(columns, widths, Vector(0, 1), 100, Unconstrained) shouldBe Vector(150, 150, 100)
  }

  it should "spill whatever the group cannot absorb into columns outside it" in {
    // Both group members cap at 130: at most 60 total headroom, so a +150 request only takes
    // 60 from the sibling outside the group (index 2), split evenly like any AllColumns delta.
    val columns = Vector(col(max = 130), col(max = 130), col())
    val widths  = Vector(100.0, 100.0, 100.0)
    resizeGroup(columns, widths, Vector(0, 1), 150, AllColumns) shouldBe Vector(130, 130, 40)
  }

  it should "resize the columns still resizable in the group and reject only if every one is locked" in {
    val columns = Vector(col(resizable = false), col(), col())
    val widths  = Vector(100.0, 100.0, 100.0)
    resizeGroup(columns, widths, Vector(0, 1), 60, FlexLastColumn) shouldBe Vector(100, 160, 40)
    resizeGroup(
      Vector(col(resizable = false), col(resizable = false)),
      Vector(100.0, 100.0),
      Vector(0, 1),
      60,
      FlexLastColumn
    ) shouldBe Vector(100.0, 100.0)
  }

  "A custom policy" should "receive every column's bounds and the requested target" in {
    var seen: Option[ColumnResizeRequest] = None
    val columns                           = Vector(col(), col(max = 200))
    val widths                            = Vector(100.0, 100.0)
    val policy: CustomColumnResizePolicy = request => {
      seen = Some(request)
      val index = request.target.get._1.head
      request.widths.updated(index, request.widths(index) + 20)
    }
    applyCustom(columns, widths, 640.0, Some((Vector(0), 20.0)), policy) shouldBe Vector(120, 100)
    seen shouldBe Some(
      ColumnResizeRequest(
        Vector(ColumnResizeSpec(40, 300, 100, true), ColumnResizeSpec(40, 200, 100, true)),
        widths,
        640.0,
        Some((Vector(0), 20.0))
      )
    )
  }

  it should "receive every visible leaf of a group as the target, not just one" in {
    var seen: Option[Vector[Int]] = None
    val policy: CustomColumnResizePolicy = request => {
      seen = Some(request.target.get._1)
      request.widths
    }
    applyCustom(Vector(col(), col(), col()), Vector(100.0, 100.0, 100.0), 300.0, Some((Vector(0, 1), 40.0)), policy)
    seen shouldBe Some(Vector(0, 1))
  }

  it should "clamp an accepted result and reject one of the wrong length atomically" in {
    val columns = Vector(col(), col())
    val widths  = Vector(100.0, 100.0)
    applyCustom(columns, widths, 800.0, None, _ => Vector(999.0, Double.NaN)) shouldBe Vector(
      300,
      100
    )
    applyCustom(columns, widths, 800.0, None, _ => Vector(150.0)) shouldBe widths
    applyCustom(columns, widths, 800.0, None, _ => Vector.empty) shouldBe widths
  }

  it should "preserve the constrained sum and bounds through repeated adjustments" in {
    ColumnResizePolicy.values.filterNot(_ == Unconstrained).foreach { policy =>
      val columns = Vector(col(), col(max = 120), col(resizable = false), col(max = 500))
      var widths  = layout(columns, 650, policy)
      val sum     = widths.sum
      (0 until 100).foreach { step =>
        widths = resize(columns, widths, step % 4, if (step % 3 == 0) -117 else 89, policy)
        widths.sum shouldBe sum +- 0.0001
        widths.zip(columns).foreach { (width, column) =>
          width should be >= column.minimum
          width should be <= column.maximum
        }
      }
    }
  }
}
