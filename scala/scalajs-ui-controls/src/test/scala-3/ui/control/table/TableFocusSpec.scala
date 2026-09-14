package ui.control.table

import ui.control.table.TableColumn.*
import ui.control.table.TableView.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.{ListDataSource, ListProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable
import scala.scalajs.js

class TableFocusSpec extends AnyFlatSpec with Matchers {
  private case class Person(name: String)
  private def mounted[S](source: ListDataSource[S])(run: TableView[S] => Unit): Unit = {
    var table: TableView[S] = null
    val root                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(source) { column[S, String]("Value") {} }
        }
      },
      new SsrCursor()
    )
    try run(table)
    finally Runtime.unmount(root)
  }

  "Row focus" should "remain independent of selection with coherent item/index snapshots" in {
    val values = ListProperty(js.Array(Person("A"), Person("B"), Person("C")))
    mounted(values) { table =>
      val focus = table.focusModel
      focus.focusedIndex shouldBe -1
      focus.focusPrevious(); focus.focusedIndex shouldBe -1
      focus.focusNext(); focus.focusedIndex shouldBe 0
      table.select(2)
      focus.focus(1)
      table.selectedIndexProperty.get shouldBe 2
      var observed     = -1
      val subscription = focus.focusedIndexProperty.observe { index =>
        observed = index
        focus.focusedItem shouldBe (if (index >= 0) values.get(index) else null)
      }
      focus.focusNext(); observed shouldBe 2
      focus.focusNext(); focus.focusedIndex shouldBe 2
      focus.focusPrevious(); focus.focusedIndex shouldBe 1
      focus.focus(Int.MaxValue); focus.focusedIndex shouldBe -1
      focus.isFocused(-1) shouldBe false
      table.selectedIndexProperty.get shouldBe 2
      subscription.dispose()
    }
  }

  it should "follow occurrences through edits and clear a removed focus" in {
    val same   = Person("same")
    val values = ListProperty(js.Array(same, same, Person("last")))
    mounted(values) { table =>
      val focus = table.focusModel
      focus.focus(1)
      values.insertAll(0, Seq(Person("x"), Person("y")))
      focus.focusedIndex shouldBe 3
      values.remove(2)
      focus.focusedIndex shouldBe 2
      values.update(2, Person("changed"))
      focus.focusedItem shouldBe values.get(2)
      values.patchInPlace(0, Seq(Person("replacement")), 1)
      focus.focusedIndex shouldBe 2
      values.remove(2)
      focus.focusedIndex shouldBe -1
      focus.focus(0); values.clear(); focus.focusedIndex shouldBe -1
    }
  }

  it should "preserve only unique reference identity across resets" in {
    val a      = Person("same"); val b = Person("same")
    val values = ListProperty(js.Array(a, b))
    mounted(values) { table =>
      val focus = table.focusModel
      focus.focus(1); values.setAll(Seq(b, a))
      focus.focusedIndex shouldBe 0
      values.setAll(Seq(b, b, a)); focus.focusedIndex shouldBe -1
      focus.focus(2); values.setAll(Seq(Person("same")))
      focus.focusedIndex shouldBe -1
    }
  }

  it should "keep stable cell coordinates across rows and column reordering" in {
    val values = ListProperty(js.Array(Person("A"), Person("B"), Person("C")))
    mounted(values) { table =>
      val first  = table.getVisibleLeafColumn(0)
      val second = new TableColumn[Person, String]("Second")
      table.columns.addOne(second)
      val focus = table.focusModel

      focus.focus(1, second)
      focus.focusedIndex shouldBe 1
      focus.focusedItem shouldBe values.get(1)
      focus.focusedColumn shouldBe second
      focus.focusedCell.asInstanceOf[TablePosition[Person]].row shouldBe 1
      focus.focusedCell.asInstanceOf[TablePosition[Person]].column shouldBe 1
      focus.isFocused(1, second) shouldBe true

      var observedColumn = -1
      val subscription   = focus.focusedCellProperty.observeWithoutInitial(position =>
        observedColumn = Option(position).fold(-1)(_.column)
      )
      table.columns.setAll(Seq(second, first))
      observedColumn shouldBe 0
      focus.focusedColumn shouldBe second

      values.insert(0, Person("Before"))
      focus.focusedCell.asInstanceOf[TablePosition[Person]].row shouldBe 2
      focus.focusedCell.asInstanceOf[TablePosition[Person]].column shouldBe 0
      second.visible = false
      focus.focusedIndex shouldBe 2
      focus.focusedColumn shouldBe null
      focus.focusedCell.asInstanceOf[TablePosition[Person]].column shouldBe -1

      focus.focus(2, first)
      values.remove(2)
      focus.focusedCell shouldBe null
      subscription.dispose()
    }
  }

  it should "navigate cell coordinates without changing selection" in {
    val values = ListProperty(js.Array("a", "b", "c"))
    mounted(values) { table =>
      val first  = table.getVisibleLeafColumn(0)
      val second = new TableColumn[String, String]("Second")
      table.columns.addOne(second)
      val focus = table.focusModel
      table.select(2)

      focus.focus(1)
      focus.focusRightCell()
      focus.focusedCell shouldBe TablePosition(table, 1, first)
      focus.focusRightCell()
      focus.focusedCell shouldBe TablePosition(table, 1, second)
      focus.focusRightCell()
      focus.focusedCell shouldBe TablePosition(table, 1, second)
      focus.focusAboveCell()
      focus.focusedCell shouldBe TablePosition(table, 0, second)
      focus.focusAboveCell()
      focus.focusedCell shouldBe TablePosition(table, 0, second)
      focus.focusBelowCell(); focus.focusPrevious()
      focus.focusedCell shouldBe TablePosition(table, 0, second)
      focus.focusLeftCell()
      focus.focusedCell shouldBe TablePosition(table, 0, first)
      table.selectedIndexProperty.get shouldBe 2

      focus.focus(1, new TableColumn[String, String]("Foreign"))
      focus.focusedCell shouldBe null
    }
  }

  it should "stop accepting focus operations after disposal" in {
    val values                             = ListProperty(js.Array("a", "b"))
    var table: TableView[String]           = null
    var alternate: TableFocusModel[String] = null
    mounted(values) { current =>
      table = current
      alternate = new TableFocusModel(current)
      current.focusModel.focus(1)
    }
    table.focusModel = alternate
    table.focusModel.focusedIndex shouldBe 1
    table.focusModel.focus(0); table.focusModel.focusPrevious()
    values.clear()
    table.focusedIndexProperty.get shouldBe 1
    table.focusedItemProperty.get shouldBe "b"
  }

  it should "replace the focus model and keep detached alternates reconciled" in {
    val values = ListProperty(js.Array("a", "b", "c"))
    mounted(values) { table =>
      val original = table.focusModel
      original.focus(1)

      final class TrackingFocusModel(owner: TableView[String]) extends TableFocusModel(owner) {
        var calls                            = 0
        override def focus(index: Int): Unit = {
          calls += 1
          super.focus(index)
        }
      }

      val replacement = new TrackingFocusModel(table)
      replacement.focus(2)
      val observed = mutable.ArrayBuffer.empty[Int]
      val observer = table.focusedIndexProperty.observeWithoutInitial(observed += _)

      table.focusModel = replacement
      table.focusModel should be theSameInstanceAs replacement
      table.focusModelProperty.get should be theSameInstanceAs replacement
      table.focusedIndexProperty.get shouldBe 2
      table.focusedItemProperty.get shouldBe "c"
      observed.last shouldBe 2

      original.focus(0)
      table.focusedIndexProperty.get shouldBe 2
      values.insert(0, "before")
      table.focusedIndexProperty.get shouldBe 3
      original.focusedIndex shouldBe 1

      table.focusModel = original
      table.focusedIndexProperty.get shouldBe 1
      table.focusedItemProperty.get shouldBe "a"
      replacement.calls shouldBe 1
      observer.dispose()
    }
  }

  it should "reject null and foreign focus models atomically" in {
    mounted(ListProperty(js.Array("a"))) { table =>
      val original = table.focusModel
      an[IllegalArgumentException] should be thrownBy {
        table.focusModel = null.asInstanceOf[TableFocusModel[String]]
      }
      table.focusModel should be theSameInstanceAs original

      mounted(ListProperty(js.Array("other"))) { other =>
        val foreign = new TableFocusModel(other)
        an[IllegalArgumentException] should be thrownBy {
          table.focusModel = foreign
        }
        table.focusModel should be theSameInstanceAs original
      }
    }
  }
}
