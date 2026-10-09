package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import ui.core.state.Disposable
import ui.forms.Input.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

private final class TestCursor(onCreate: TestHostElement => Unit) extends Cursor {
  override def claimElement(tag: String): HostElement = {
    val host = new TestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new TestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}

