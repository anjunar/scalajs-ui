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

private abstract class TabsTestRoot extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      content
    }
}
