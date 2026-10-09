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

private final class TabsEventCursor(onCreate: TabsTestHostElement => Unit) extends Cursor {
  override def claimElement(tag: String): HostElement = {
    val host = new TabsTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new TabsTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}

