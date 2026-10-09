package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Anchor.*
import ui.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import ui.core.state.{Disposable, Property}
import ui.router.RouterLink.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

private final class LinkTestTextNode(private var value: String) extends TextNode {
  override def setText(next: String): Unit = value = next
  override def getText: String             = value
  override def renderHtml(): String        = value
}

