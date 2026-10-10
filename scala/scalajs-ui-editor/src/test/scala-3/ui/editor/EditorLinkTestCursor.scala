package ui.editor

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.context.UrlScope
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, HostElement, HostNode, SsrCursor, TextNode, UiEvent}
import ui.core.state.{Disposable, Property}
import ui.editor.Editor.*
import ui.editor.plugins.*
import ui.forms.{Control, ErrorResponse, Form, FormController}
import ui.forms.Form.form
import ui.viewport.Viewport
import ui.viewport.Viewport.viewport
import org.scalajs.dom.HTMLElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

private final class EditorLinkTestCursor(onCreate: EditorLinkTestHostElement => Unit)
    extends Cursor {
  override def isBrowser: Boolean = true

  override def claimElement(tag: String): HostElement = {
    val host = new EditorLinkTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new EditorLinkTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}
