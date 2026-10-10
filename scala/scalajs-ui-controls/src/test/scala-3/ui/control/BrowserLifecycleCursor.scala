package ui.control

import ui.control.datagrid.DataGrid
import ui.control.table.TableView
import ui.control.virtuallist.VirtualListView
import ui.control.virtualized.{CollectionDisplayMode, FixedRowGeometry, ItemGeometry, VirtualizedCollection}
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable.ArrayBuffer
import ui.core.render.HostElement
import ui.core.render.TextNode
private final class BrowserLifecycleCursor(deferred: Boolean) extends Cursor {

  private val callbacks = ArrayBuffer.empty[() => Unit]
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

  override def claimElement(tag: String): HostElement =
    throw new UnsupportedOperationException("BrowserLifecycleCursor does not render")

  override def claimText(initial: String): TextNode =
    throw new UnsupportedOperationException("BrowserLifecycleCursor does not render")

  override def sub(host: HostElement): Cursor = this
}
