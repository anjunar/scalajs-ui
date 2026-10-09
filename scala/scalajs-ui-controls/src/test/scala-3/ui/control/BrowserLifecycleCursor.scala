package ui.control

import ui.control.datagrid.DataGrid
import ui.control.table.TableView
import ui.control.virtuallist.VirtualListView
import ui.control.virtualized.{
  CollectionDisplayMode,
  FixedRowGeometry,
  ItemGeometry,
  VirtualizedCollection
}
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
private final class BrowserLifecycleCursor(deferred: Boolean) extends Cursor {

  private val callbacks = scala.collection.mutable.ArrayBuffer.empty[() => Unit]
  private var completed = !deferred

  override def isBrowser: Boolean   = true
  override def isHydrating: Boolean = !completed

  override def afterHydration(callback: () => Unit): Unit =
    if (completed) callback()
    else callbacks += callback

  override def completeHydration(): Unit = {
    completed = true
    val pending = callbacks.toVector
    callbacks.clear()
    pending.foreach(_())
  }

  override def claimElement(tag: String): ui.core.render.HostElement =
    throw new UnsupportedOperationException("BrowserLifecycleCursor does not render")

  override def claimText(initial: String): ui.core.render.TextNode =
    throw new UnsupportedOperationException("BrowserLifecycleCursor does not render")

  override def sub(host: ui.core.render.HostElement): Cursor = this
}
