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

private final class LinkTestCursor(onCreate: LinkTestHostElement => Unit) extends Cursor {
  override def claimElement(tag: String): HostElement = {
    val host = new LinkTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new LinkTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}
