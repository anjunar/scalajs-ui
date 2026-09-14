package ui.control.table

import ui.control.table.TableColumn.*
import ui.control.table.TableView.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.{Disposable, ListDataSource, ListProperty, Property, ReadOnlyProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class TableCellSpec extends AnyFlatSpec with Matchers {

  private final class Person(val name: Property[String], var snapshot: String = "initial")

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

  "TableCell values" should "observe values and detach the old binding when the factory or row changes" in {
    val first                                   = new Person(Property("Ada"))
    val replacement                             = new Person(Property("Grace"))
    val values                                  = ListProperty(js.Array(first))
    val observed                                = new CountingValue(first.name)
    val alternative                             = new CountingValue(Property("Augusta"))
    var nameColumn: TableColumn[Person, String] = null

    val (root, table, cursor) = mountTable(values) {
      nameColumn = column[Person, String]("Name") {
        cellValueFactory = _ => observed
      }
    }
    observed.listeners shouldBe 1
    cursor.collectHtml() should include("Ada")
    first.name.set("Lovelace")
    cursor.collectHtml() should include("Lovelace")
    nameColumn.getCellData(0) shouldBe "Lovelace"
    nameColumn.getCellObservableValue(first) shouldBe observed
    nameColumn.getCellData(99) shouldBe null

    nameColumn.cellValueFactoryProperty.set(Some(_ => alternative))
    observed.listeners shouldBe 0
    alternative.listeners shouldBe 1
    cursor.collectHtml() should include("Augusta")

    nameColumn.cellValueFactoryProperty.set(Some(features => features.value.name))
    alternative.listeners shouldBe 0
    values.update(0, replacement)
    first.name.set("obsolete")
    cursor.collectHtml() should not include "obsolete"
    replacement.name.set("Hopper")
    cursor.collectHtml() should include("Hopper")

    nameColumn.cellValueFactoryProperty.set(Some(_ => observed))
    Runtime.unmount(root)
    observed.listeners shouldBe 0
    nameColumn.tableViewProperty.get shouldBe null
  }

  it should "retain overlapping row cells through scrolling, viewport measurement and height changes" in {
    val values                = ListProperty(js.Array((0 until 60)*))
    val cells                 = mutable.Map.empty[Int, TableCell[Int, Int]]
    val mounts                = mutable.Map.empty[Int, Int].withDefaultValue(0)
    val (root, table, cursor) = mountTable(values) {
      scrolling = true
      summon[TableView[Int]].viewportHeightProperty.set(64)
      column[Int, Int]("Index") {
        cellValueFactory = features => Property(features.value)
        cellFactory = _ =>
          new TableCell[Int, Int] {
            override protected def renderContent(using AbstractComponent, Cursor): Unit = {
              val index = indexProperty.get
              cells(index) = this
              mounts(index) += 1
              text(itemProperty.map(_.toString)) {}
            }
          }
      }
    }
    val retained = cells(3)
    val first    = cells(0)
    retained.tableView shouldBe table
    retained.tableColumn.tableViewProperty.get shouldBe table
    retained.tableRow.indexProperty.get shouldBe 3

    table.scrollTopProperty.set(256)
    cells(3) should be theSameInstanceAs retained
    mounts(3) shouldBe 1
    first.isDisposed shouldBe true
    cells.contains(14) shouldBe true

    table.scrollTopProperty.set(64)
    cells(3) should be theSameInstanceAs retained
    mounts(0) shouldBe 2
    table.applyViewportSize(1200, 96)
    table.rowHeightProperty.set(40)
    cells(3) should be theSameInstanceAs retained
    retained.host.style("width") shouldBe Some("1200px")
    cursor.collectHtml() should include("top: 120px")

    Runtime.unmount(root)
    retained.isDisposed shouldBe true
  }

  it should "attach direct column additions, detach their subscriptions and support reattachment" in {
    val values                = ListProperty(js.Array("Ada"))
    val (root, table, cursor) = mountTable(values) {}
    val nameColumn            = new TableColumn[String, String]("Name")
    nameColumn.cellValueFactoryProperty.set(Some(features => Property(features.value)))
    table.columns.addOne(nameColumn)
    nameColumn.tableViewProperty.get shouldBe table
    cursor.collectHtml() should include("Ada")

    var widthUpdates = 0
    val subscription = table.renderedWidthsProperty.observe(_ => widthUpdates += 1)
    table.columns.remove(0)
    nameColumn.tableViewProperty.get shouldBe null
    nameColumn.isDisposed shouldBe false
    val detachedUpdates = widthUpdates
    nameColumn.prefWidthProperty.set(500)
    widthUpdates shouldBe detachedUpdates
    cursor.collectHtml() should not include "Ada"

    table.columns.addOne(nameColumn)
    cursor.collectHtml() should include("Ada")
    val attachedUpdates = widthUpdates
    nameColumn.prefWidthProperty.set(300)
    widthUpdates shouldBe attachedUpdates + 1
    subscription.dispose()
    Runtime.unmount(root)
    nameColumn.isDisposed shouldBe true
  }

  it should "reject invalid column mutations before changing the list or its mounted cells" in {
    val values                            = ListProperty(js.Array("Ada"))
    var name: TableColumn[String, String] = null
    val (root, table, cursor)             = mountTable(values) {
      name = column[String, String]("Name") { cell(value => text(value) {}) }
    }
    val (otherRoot, other, _) = mountTable(values) {}
    val disposed              = new TableColumn[String, String]("Disposed")
    disposed.dispose()
    val before = cursor.collectHtml()
    intercept[IllegalArgumentException](table.columns.addOne(name))
    intercept[IllegalArgumentException](table.columns.insert(0, name))
    intercept[IllegalArgumentException](table.columns.insertAll(0, Seq(name)))
    intercept[IllegalArgumentException](table.columns.setAll(Seq(name, name)))
    intercept[IllegalArgumentException](table.columns.patchInPlace(0, Seq(name, name), 1))
    intercept[IllegalArgumentException](table.columns.update(0, disposed))
    intercept[IllegalArgumentException](other.columns.addOne(name))
    table.columns.toVector shouldBe Vector(name)
    other.columns shouldBe empty
    name.tableViewProperty.get shouldBe table
    cursor.collectHtml() shouldBe before
    table.columns.clear()
    other.columns.addOne(name)
    name.tableViewProperty.get shouldBe other
    Runtime.unmount(root)
    name.isDisposed shouldBe false
    Runtime.unmount(otherRoot)
    name.isDisposed shouldBe true
  }

  "TableColumn trees" should "derive ownership, visible leaves, group widths and multi-row headers" in {
    val values                              = ListProperty(js.Array("Ada"))
    var group: TableColumn[String, Any]     = null
    var first: TableColumn[String, String]  = null
    var second: TableColumn[String, String] = null
    val (root, table, cursor)               = mountTable(values) {
      group = columnGroup[String]("Identity") {
        first = column[String, String]("First") {
          prefWidth = 120
          cell(value => text(value) {})
        }
        second = column[String, String]("Second") {
          prefWidth = 180
          cell(value => text(value.reverse) {})
        }
      }
    }

    table.columns.toVector shouldBe Vector(group)
    group.columns.toVector shouldBe Vector(first, second)
    first.parentColumn shouldBe group
    second.parentColumn shouldBe group
    group.parentColumn shouldBe null
    Seq(group, first, second).foreach(_.tableViewProperty.get shouldBe table)
    table.visibleLeafColumns.get shouldBe Vector(first, second)
    group.width shouldBe (first.width + second.width +- 0.001)

    val initialHtml = cursor.collectHtml()
    initialHtml should include("ui-table-header-cell-group")
    initialHtml should include("aria-colspan=\"2\"")
    initialHtml should include("grid-row: 1 / span 1")
    initialHtml should include("grid-row: 2 / span 1")
    initialHtml should include("aria-rowcount=\"3\"")

    first.visible = false
    table.visibleLeafColumns.get shouldBe Vector(second)
    group.width shouldBe (second.width +- 0.001)
    cursor.collectHtml() should not include "First"

    group.visible = false
    table.visibleLeafColumns.get shouldBe empty
    cursor.collectHtml() should include("ui-table-placeholder")
    Runtime.unmount(root)
    Seq(group, first, second).foreach(_.isDisposed shouldBe true)
  }

  it should "validate dynamic child mutations atomically and preserve detached subtrees" in {
    val values                = ListProperty(js.Array("Ada"))
    val (root, table, cursor) = mountTable(values) {}
    val group                 = new TableColumn[String, Any]("Group")
    val first                 = new TableColumn[String, String]("First")
    val second                = new TableColumn[String, String]("Second")
    first.setCellRenderer(value => text(value) {})
    second.setCellRenderer(value => text(value.reverse) {})
    group.columns.setAll(Seq(first, second))
    table.columns.addOne(group)

    table.visibleLeafColumns.get shouldBe Vector(first, second)
    intercept[IllegalArgumentException](group.columns.addOne(first))
    intercept[IllegalArgumentException](first.columns.addOne(group))
    group.columns.toVector shouldBe Vector(first, second)
    table.columns.toVector shouldBe Vector(group)

    group.columns.remove(0) shouldBe first
    first.parentColumn shouldBe null
    first.tableViewProperty.get shouldBe null
    first.isDisposed shouldBe false
    table.visibleLeafColumns.get shouldBe Vector(second)
    cursor.collectHtml() should not include "First"

    group.columns.insert(0, first)
    first.parentColumn shouldBe group
    first.tableViewProperty.get shouldBe table
    table.columns.clear()
    Seq(group, first, second).foreach(_.tableViewProperty.get shouldBe null)
    group.columns.toVector shouldBe Vector(first, second)

    val (otherRoot, other, _) = mountTable(values) {}
    other.columns.addOne(group)
    Seq(group, first, second).foreach(_.tableViewProperty.get shouldBe other)
    Runtime.unmount(root)
    Seq(group, first, second).foreach(_.isDisposed shouldBe false)
    Runtime.unmount(otherRoot)
    Seq(group, first, second).foreach(_.isDisposed shouldBe true)
  }

  it should "compose arbitrarily nested groups through the Scala DSL" in {
    val values                             = ListProperty(js.Array("Ada"))
    var outer: TableColumn[String, Any]    = null
    var inner: TableColumn[String, Any]    = null
    var first: TableColumn[String, String] = null
    var last: TableColumn[String, String]  = null
    var year: TableColumn[String, String]  = null
    val (root, table, cursor)              = mountTable(values) {
      outer = columnGroup[String]("Person") {
        inner = columnGroup[String]("Name") {
          first = column[String, String]("First") {}
          last = column[String, String]("Last") {}
        }
        year = column[String, String]("Year") {}
      }
    }

    outer.columns.toVector shouldBe Vector(inner, year)
    inner.columns.toVector shouldBe Vector(first, last)
    table.visibleLeafColumns.get shouldBe Vector(first, last, year)
    cursor.collectHtml() should include("aria-colspan=\"3\"")
    cursor.collectHtml() should include("aria-rowspan=\"2\"")
    cursor.collectHtml() should include("grid-template-rows: repeat(3,")
    Runtime.unmount(root)
  }

  it should "refresh unobserved data in both value columns and existing row renderers" in {
    val person                = new Person(Property("Ada"))
    val values                = ListProperty(js.Array(person))
    val (root, table, cursor) = mountTable(values) {
      column[Person, String]("Value") {
        cellValueFactory = features => Property("value:" + features.value.snapshot)
      }
      column[Person, String]("Renderer") {
        cell { row => text("renderer:" + row.snapshot) {} }
      }
    }
    person.snapshot = "updated"
    table.refresh()
    cursor.collectHtml() should include("value:updated")
    cursor.collectHtml() should include("renderer:updated")
    person.snapshot = "notified"
    values.notified()
    cursor.collectHtml() should include("value:notified")
    cursor.collectHtml() should include("renderer:notified")
    Runtime.unmount(root)
    table.refresh()
  }

  it should "replace only the affected column's renderer" in {
    val values                               = ListProperty(js.Array("Ada"))
    var renders                              = 0
    var changed: TableColumn[String, String] = null
    val (root, table, cursor)                = mountTable(values) {
      column[String, String]("Unchanged") {
        cell { value => renders += 1; text(value) {} }
      }
      changed = column[String, String]("Changed") {
        cell { value => text("before:" + value) {} }
      }
    }
    changed.setCellRenderer(value => text("after:" + value) {})
    renders shouldBe 1
    cursor.collectHtml() should include("after:Ada")
    cursor.collectHtml() should not include "before:Ada"
    Runtime.unmount(root)
  }

  it should "project visibility into headers, cells, widths and leaf lookups without remounting other cells" in {
    val source                                = ListProperty(js.Array("Ada"))
    val shown                                 = Property(false)
    var first: TableColumn[String, String]    = null
    var second: TableColumn[String, String]   = null
    val observed                              = new CountingValue(Property("observed"))
    var firstMounts                           = 0
    var secondMounts                          = 0
    var secondCell: TableCell[String, String] = null
    val (root, table, cursor)                 = mountTable(source) {
      first = column[String, String]("First") {
        visible = shown
        cellValueFactory = _ => observed
        cell { value => firstMounts += 1; text("first:" + value) {} }
      }
      second = column[String, String]("Second") {
        cellFactory = _ =>
          new TableCell[String, String] {
            override protected def renderContent(using AbstractComponent, Cursor): Unit = {
              secondMounts += 1
              secondCell = this
              text("second") {}
            }
          }
      }
    }
    table.visibleLeafColumns.get shouldBe Vector(second)
    table.getVisibleLeafIndex(first) shouldBe -1
    table.getVisibleLeafColumn(-1) shouldBe null
    table.getVisibleLeafColumn(1) shouldBe null
    table.getVisibleLeafColumn(0) shouldBe second
    cursor.collectHtml() should not include "First"
    firstMounts shouldBe 0
    observed.listeners shouldBe 0
    table.renderedWidthsProperty.get shouldBe Vector(800.0)
    val retained = secondCell

    shown.set(true)
    table.visibleLeafColumns.get shouldBe Vector(first, second)
    table.getVisibleLeafIndex(second) shouldBe 1
    firstMounts shouldBe 1
    observed.listeners shouldBe 1
    secondCell should be theSameInstanceAs retained
    secondMounts shouldBe 1
    retained.host.style("width") shouldBe Some("400px")
    cursor.collectHtml() should include("first:Ada")

    first.visible = false
    observed.listeners shouldBe 0
    retained.host.style("width") shouldBe Some("800px")
    secondMounts shouldBe 1
    first.tableViewProperty.get shouldBe table
    first.isDisposed shouldBe false
    Runtime.unmount(root)
  }

  it should "show the placeholder without visible columns and restore rows and selection when shown again" in {
    val source                            = ListProperty(js.Array("Ada"))
    var name: TableColumn[String, String] = null
    val (root, table, cursor)             = mountTable(source) {
      placeholder { text("No visible data") {} }
      name = column[String, String]("Name") { cell(value => text(value) {}) }
    }
    table.select(0)
    name.visible = false
    cursor.collectHtml() should include("No visible data")
    cursor.collectHtml() should not include "ui-table-row-slot"
    table.selectedItemProperty.get shouldBe "Ada"
    table.renderedWidthsProperty.get shouldBe Vector.empty
    name.visible = true
    cursor.collectHtml() should not include "No visible data"
    cursor.collectHtml() should include("ui-table-row-selected")
    cursor.collectHtml() should include("Ada")
    Runtime.unmount(root)
  }

  it should "release visibility listeners on detach and keep the visible projection ordered after replacement" in {
    val source                = ListProperty(js.Array("Ada"))
    val (root, table, cursor) = mountTable(source) {}
    val first                 = new TableColumn[String, String]("First")
    val hidden                = new TableColumn[String, String]("Hidden")
    val last                  = new TableColumn[String, String]("Last")
    hidden.visible = false
    table.columns.setAll(Seq(first, hidden, last))
    table.visibleLeafColumns.get shouldBe Vector(first, last)
    table.columns.setAll(Seq(last, first))
    table.visibleLeafColumns.get shouldBe Vector(last, first)
    var changes      = 0
    val subscription = table.visibleLeafColumns.observeWithoutInitial(_ => changes += 1)
    hidden.visible = true
    changes shouldBe 0
    table.visibleLeafColumns.get shouldBe Vector(last, first)
    table.columns.addOne(hidden)
    table.visibleLeafColumns.get shouldBe Vector(last, first, hidden)
    subscription.dispose()
    Runtime.unmount(root)
  }

  it should "rebind row, cell, CSS and ARIA state when the selection model changes" in {
    val source                                  = ListProperty(js.Array("Ada"))
    var name: TableColumn[String, String]       = null
    var renderedCell: TableCell[String, String] = null
    val (root, table, cursor)                   = mountTable(source) {
      name = column[String, String]("Name") {
        cellFactory = _ =>
          new TableCell[String, String] {
            override protected def renderContent(using AbstractComponent, Cursor): Unit = {
              renderedCell = this
              text("Ada") {}
            }
          }
      }
    }

    val original = table.selectionModel
    original.select(0)
    cursor.collectHtml() should include("ui-table-row-selected")
    renderedCell.selectedProperty.get shouldBe false

    val replacement = new TableSelectionModel(table)
    replacement.selectionMode = TableSelectionMode.Multiple
    replacement.cellSelectionEnabled = true
    replacement.select(0, name)
    table.selectionModel = replacement

    renderedCell.selectedProperty.get shouldBe true
    cursor.collectHtml() should include("ui-table-cell-selected")
    cursor.collectHtml() should not include "ui-table-row-selected"
    table.host.attribute("aria-multiselectable") shouldBe Some("true")
    table.host.attribute("class").get should include("ui-table-view-cell-selection")

    table.selectionModel = original
    renderedCell.selectedProperty.get shouldBe false
    cursor.collectHtml() should include("ui-table-row-selected")
    table.host.attribute("aria-multiselectable") shouldBe Some("false")
    table.host.attribute("class").get should not include "ui-table-view-cell-selection"
    Runtime.unmount(root)
  }

  private final class CountingValue(underlying: Property[String]) extends ReadOnlyProperty[String] {
    var listeners                                              = 0
    override def get: String                                   = underlying.get
    override def observe(observer: String => Unit): Disposable = {
      listeners += 1
      val subscription = underlying.observe(observer)
      Disposable { listeners -= 1; subscription.dispose() }
    }
    override def observeWithoutInitial(observer: String => Unit): Disposable = {
      listeners += 1
      val subscription = underlying.observeWithoutInitial(observer)
      Disposable { listeners -= 1; subscription.dispose() }
    }
  }
}
