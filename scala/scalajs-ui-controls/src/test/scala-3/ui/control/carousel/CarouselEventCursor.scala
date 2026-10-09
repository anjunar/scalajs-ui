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

private final class CarouselEventCursor(
    onCreate: CarouselTestHostElement => Unit,
    browser: Boolean = false
) extends Cursor {
  override def isBrowser: Boolean = browser

  override def claimElement(tag: String): HostElement = {
    val host = new CarouselTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new CarouselTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}

