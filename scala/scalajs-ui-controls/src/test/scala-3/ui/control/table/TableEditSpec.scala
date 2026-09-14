package ui.control.table

import ui.control.table.TableColumn.*
import ui.control.table.TableView.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.{ListDataSource, ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class TableEditSpec extends AnyFlatSpec with Matchers {

  private final class Person(val name: Property[String])

  private def mountTable[S](source: ListDataSource[S])(
      configure: TableView[S] ?=> Cursor ?=> Unit
  ): (AbstractComponent, TableView[S], SsrCursor) = {
    val cursor              = new SsrCursor()
    var table: TableView[S] = null
    val root                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(source)(configure)
        }
      },
      cursor
    )
    (root, table, cursor)
  }

  "Table editing" should "publish one session and commit through a writable cell property" in {
    val person                                       = new Person(Property("Ada"))
    val source                                       = ListProperty(js.Array(person))
    var name: TableColumn[Person, String]            = null
    var start: TableEditStartEvent[Person, String]   = null
    var commit: TableEditCommitEvent[Person, String] = null
    val (root, table, cursor)                        = mountTable(source) {
      summon[TableView[Person]].editableProperty.set(true)
      name = column[Person, String]("Name") {
        cellValueFactory = _.value.name
        onEditStart(event => start = event)
        onEditCommit(event => commit = event)
      }
    }

    table.edit(0, name) shouldBe true
    table.editingCellProperty.get shouldBe TablePosition(table, 0, name)
    table.editingItemProperty.get shouldBe person
    table.originalEditValueProperty.get shouldBe "Ada"
    table.editingValueProperty.get shouldBe "Ada"
    start.rowValue shouldBe person
    start.oldValue shouldBe "Ada"
    cursor.collectHtml() should include("ui-table-cell-editing")

    table.updateEdit("Grace") shouldBe true
    table.editingValueProperty.get shouldBe "Grace"
    table.commitEdit() shouldBe true
    person.name.get shouldBe "Grace"
    commit.oldValue shouldBe "Ada"
    commit.newValue shouldBe "Grace"
    table.editingCellProperty.get shouldBe null
    cursor.collectHtml() should not include "ui-table-cell-editing"

    Runtime.unmount(root)
  }

  it should "let a custom commit handler replace default write-back without hiding observers" in {
    val person                            = new Person(Property("Ada"))
    val source                            = ListProperty(js.Array(person))
    val handled                           = mutable.ArrayBuffer.empty[String]
    val observed                          = mutable.ArrayBuffer.empty[String]
    var name: TableColumn[Person, String] = null
    val (root, table, _)                  = mountTable(source) {
      summon[TableView[Person]].editableProperty.set(true)
      name = column[Person, String]("Name") {
        cellValueFactory = features => features.value.name.map(identity)
        editCommitHandler(event => handled += event.newValue)
        onEditCommit(event => observed += event.newValue)
      }
    }

    table.edit(0, name) shouldBe true
    table.commitEdit("Grace") shouldBe true
    person.name.get shouldBe "Ada"
    handled shouldBe Seq("Grace")
    observed shouldBe Seq("Grace")

    name.editCommitHandlerProperty.set(None)
    table.edit(0, name) shouldBe true
    table.commitEdit("Augusta") shouldBe false
    table.editingValueProperty.get shouldBe "Augusta"
    table.cancelEdit() shouldBe true
    table.cancelEdit() shouldBe false
    Runtime.unmount(root)
  }

  it should "rebase across inserts and cancel with structural and availability reasons" in {
    val first  = new Person(Property("Ada"))
    val target = new Person(Property("Grace"))
    val source = ListProperty(
      js.Array(first, target, new Person(Property("After")))
    )
    val reasons                           = mutable.ArrayBuffer.empty[TableEditCancelReason]
    var name: TableColumn[Person, String] = null
    val (root, table, _)                  = mountTable(source) {
      summon[TableView[Person]].editableProperty.set(true)
      name = column[Person, String]("Name") {
        cellValueFactory = _.value.name
        onEditCancel(event => reasons += event.reason)
      }
    }

    table.edit(1, name) shouldBe true
    source.insert(0, new Person(Property("Before")))
    table.editingCellProperty.get.row shouldBe 2
    table.editingItemProperty.get shouldBe target
    source.remove(2)
    reasons.last shouldBe TableEditCancelReason.RowRemoved

    table.edit(0, name) shouldBe true
    source.update(0, new Person(Property("Replacement")))
    reasons.last shouldBe TableEditCancelReason.RowReplaced

    table.edit(0, name) shouldBe true
    name.visible = false
    reasons.last shouldBe TableEditCancelReason.ColumnUnavailable

    name.visible = true
    table.edit(0, name) shouldBe true
    name.editable = false
    reasons.last shouldBe TableEditCancelReason.ColumnDisabled

    name.editable = true
    table.edit(0, name) shouldBe true
    table.editableProperty.set(false)
    reasons.last shouldBe TableEditCancelReason.TableDisabled

    table.editableProperty.set(true)
    table.edit(0, name) shouldBe true
    source.setAll(Seq(new Person(Property("Reset"))))
    reasons.last shouldBe TableEditCancelReason.SourceReset
    Runtime.unmount(root)
  }

  it should "reject unavailable targets and cancel a live cell when the table is disposed" in {
    val person                                  = new Person(Property("Ada"))
    val source                                  = ListProperty(js.Array(person))
    var name: TableColumn[Person, String]       = null
    var reason: TableEditCancelReason           = null
    var renderedCell: TableCell[Person, String] = null
    val (root, table, _)                        = mountTable(source) {
      name = column[Person, String]("Name") {
        cellValueFactory = _.value.name
        cellFactory = _ => {
          renderedCell = new TableCell[Person, String]
          renderedCell.editable = false
          renderedCell
        }
        onEditCancel(event => reason = event.reason)
      }
    }
    table.edit(0, name) shouldBe false
    table.editableProperty.set(true)
    table.edit(9, name) shouldBe false
    table.edit(0, new TableColumn[Person, String]("Foreign")) shouldBe false
    table.edit(0, name) shouldBe false
    renderedCell.editable = true
    table.edit(0, name) shouldBe true
    renderedCell.editable = false
    reason shouldBe TableEditCancelReason.CellUnavailable
    renderedCell.editable = true
    table.edit(0, name) shouldBe true
    Runtime.unmount(root)
    reason shouldBe TableEditCancelReason.Disposed
    table.commitEdit("late") shouldBe false
    table.cancelEdit() shouldBe false
  }
}
