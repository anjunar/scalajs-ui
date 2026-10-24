package ui.control

import ui.control.tabs.Tabs
import ui.control.tabs.Tabs.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, HostElement, HostNode, SsrCursor, TextNode, UiEvent}
import ui.core.state.{Disposable, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

/** Renders into event-capable test hosts. With `browser`, components see a browser cursor; with
  * `deferredHydration`, `afterHydration` callbacks wait for [[completeHydration]] the way they do
  * under a real HydratingCursor.
  */
private final class TabsEventCursor(
    onCreate: TabsTestHostElement => Unit,
    browser: Boolean = false,
    deferredHydration: Boolean = false
) extends Cursor {
  private val callbacks = mutable.ArrayBuffer.empty[() => Unit]
  private var completed = !deferredHydration

  override def isBrowser: Boolean   = browser
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

  override def claimElement(tag: String): HostElement = {
    val host = new TabsTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new TabsTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}
