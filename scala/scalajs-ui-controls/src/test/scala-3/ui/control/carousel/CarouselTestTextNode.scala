package ui.control.carousel

import ui.control.carousel.Carousel.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, HostElement, HostNode, SsrCursor, TextNode, UiEvent}
import ui.core.state.{Disposable, ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

private final class CarouselTestTextNode(private var value: String) extends TextNode {
  override def setText(next: String): Unit = value = next
  override def getText: String             = value
  override def renderHtml(): String        = value
}

