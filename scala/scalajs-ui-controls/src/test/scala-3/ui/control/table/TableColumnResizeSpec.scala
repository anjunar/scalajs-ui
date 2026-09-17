package ui.control.table

import ui.control.table.TableView.*
import ui.control.table.TableColumn.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.scalajs.js

class TableColumnResizeSpec extends AnyFlatSpec with Matchers {
  private def mounted(
      run: (TableView[String], TableColumn[String, String], TableColumn[String, String]) => Unit
  ): Unit = {
    var table: TableView[String]            = null
    var first: TableColumn[String, String]  = null
    var second: TableColumn[String, String] = null
    val root                                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(ListProperty(js.Array("row"))) {
            first = column[String, String]("First") { prefWidth = 200 }
            second = column[String, String]("Second") { prefWidth = 200 }
          }
        }
      },
      new SsrCursor()
    )
    try run(table, first, second)
    finally Runtime.unmount(root)
  }

  "Table column widths" should "separate requested widths from preferred widths and survive measurements" in mounted {
    (table, first, second) =>
      table.applyViewportSize(400, 200)
      table.resizeColumn(first, 50) shouldBe true
      table.renderedWidthsProperty.get shouldBe Vector(250, 150)
      first.width shouldBe 250
      first.prefWidth shouldBe 200
      table.applyViewportSize(400, 220)
      first.width shouldBe 250
      table.applyViewportSize(500, 220)
      table.renderedWidthsProperty.get.sum shouldBe 500.0 +- 0.001
      first.prefWidth = 100
      table.columnResizePolicyProperty.set(ColumnResizePolicy.Unconstrained)
      first.width shouldBe 100
      second.width shouldBe 150
  }

  it should "retain hidden widths, release detached ownership and respect changing limits" in mounted {
    (table, first, second) =>
      table.columnResizePolicyProperty.set(ColumnResizePolicy.Unconstrained)
      table.resizeColumn(first, 50) shouldBe true
      first.visible = false
      table.resizeColumn(first, 20) shouldBe false
      first.visible = true
      first.width shouldBe 250
      first.maxWidth = 220
      first.width shouldBe 220
      first.resizable = false
      table.resizeColumn(first, -30) shouldBe false
      table.columns.remove(0)
      first.width shouldBe 200
      first.resizable = true
      table.columns.insert(0, first)
      first.width shouldBe 200
      table.resizeColumn(new TableColumn[String, String](), 20) shouldBe false
  }

  it should "ignore requests after unmount" in mounted { (table, first, _) =>
    Runtime.unmount(table)
    table.resizeColumn(first, 50) shouldBe false
    table.autoFitColumn(first) shouldBe false
  }

  it should "leave server widths unchanged for browser-only content fitting" in mounted {
    (table, first, _) =>
      val before = table.renderedWidthsProperty.get
      table.autoFitColumn(first) shouldBe false
      table.renderedWidthsProperty.get shouldBe before
  }

  private def mountedGrouped(
      run: (
          TableView[String],
          TableColumn[String, Any],
          TableColumn[String, String],
          TableColumn[String, String],
          TableColumn[String, String]
      ) => Unit
  ): Unit = {
    var table: TableView[String]           = null
    var group: TableColumn[String, Any]    = null
    var first: TableColumn[String, String] = null
    var second: TableColumn[String, String] = null
    var third: TableColumn[String, String]  = null
    val root                                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(ListProperty(js.Array("row"))) {
            group = columnGroup("Group") {
              first = column[String, String]("First") { prefWidth = 200; maxWidth = 250 }
              second = column[String, String]("Second") { prefWidth = 200; maxWidth = 250 }
            }
            third = column[String, String]("Third") { prefWidth = 200 }
          }
        }
      },
      new SsrCursor()
    )
    try run(table, group, first, second, third)
    finally Runtime.unmount(root)
  }

  "A group's resize handle" should "share its delta across its own children, unconstrained leaving a sibling alone" in mountedGrouped {
    (table, group, first, second, third) =>
      table.applyViewportSize(600, 200)
      table.columnResizePolicyProperty.set(ColumnResizePolicy.Unconstrained)
      table.resizeColumn(group, 50) shouldBe true
      first.width shouldBe 225
      second.width shouldBe 225
      third.width shouldBe 200
  }

  it should "compensate a sibling for exactly what its own children grew by, under a constrained policy" in mountedGrouped {
    (table, group, first, second, third) =>
      table.applyViewportSize(600, 200)
      table.columnResizePolicyProperty.set(ColumnResizePolicy.AllColumns)
      // A constrained policy keeps the table's total width fixed: growing the group by 50 (25 to
      // each child, shared proportionally) always shrinks something else by 50, the same as it
      // would for resizing any single column.
      table.resizeColumn(group, 50) shouldBe true
      first.width shouldBe 225
      second.width shouldBe 225
      third.width shouldBe 150
  }

  it should "cap the group's own growth at what its children can hold, before asking a sibling for the rest" in mountedGrouped {
    (table, group, first, second, third) =>
      table.applyViewportSize(600, 200)
      table.columnResizePolicyProperty.set(ColumnResizePolicy.AllColumns)
      // Both children cap at 250: 50 headroom each, 100 total. The group can only ever grow by
      // that 100, so the sibling outside it gives up exactly 100, not the originally dragged 150.
      table.resizeColumn(group, 150) shouldBe true
      first.width shouldBe 250
      second.width shouldBe 250
      third.width shouldBe 100
  }

  it should "respect the group's own resizable flag independently of its children" in mountedGrouped {
    (table, group, first, second, third) =>
      table.applyViewportSize(600, 200)
      group.resizable = false
      table.resizeColumn(group, 50) shouldBe false
      first.width shouldBe 200
      second.width shouldBe 200
  }

  "A custom resize policy" should "replace the built-in strategy for both layout and resize" in mounted {
    (table, first, second) =>
      // A pure re-layout (no target) leaves widths alone; an actual resize grows its own target
      // by delta and shrinks every other column by twice that -- deliberately not a strategy any
      // built-in policy produces, to demonstrate this genuinely replaces it rather than just
      // reading the same result back.
      val policy: CustomColumnResizePolicy = request =>
        request.target match {
          case Some((indices, delta)) =>
            request.widths.zipWithIndex.map((w, i) => if (indices.contains(i)) w + delta else w - delta * 2)
          case None => request.widths
        }
      table.customResizePolicyProperty.set(Some(policy))
      table.applyViewportSize(400, 200)
      table.resizeColumn(first, 30) shouldBe true
      table.renderedWidthsProperty.get shouldBe Vector(230, 140)
  }

  it should "reject a malformed result and keep the previous widths" in mounted { (table, first, _) =>
    table.applyViewportSize(400, 200)
    val before = table.renderedWidthsProperty.get
    table.customResizePolicyProperty.set(Some(_ => Vector(1.0)))
    table.resizeColumn(first, 30) shouldBe false
    table.renderedWidthsProperty.get shouldBe before
  }

  it should "fall back to the built-in strategy once cleared" in mounted { (table, first, second) =>
    table.customResizePolicyProperty.set(Some(request => request.widths.map(_ => 150.0)))
    table.applyViewportSize(400, 200)
    table.customResizePolicyProperty.set(None)
    table.resizeColumn(first, 50) shouldBe true
    table.renderedWidthsProperty.get shouldBe Vector(250, 150)
  }
}
