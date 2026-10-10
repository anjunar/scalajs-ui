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

private abstract class InputRoot extends AbstractComponent {
  val tagName = "div"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      content
    }
}
