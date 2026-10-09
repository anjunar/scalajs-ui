package ui.control.virtuallist

import ui.control.CrawlTestRoot

import ui.control.virtuallist.VirtualListView.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.remote.{RemoteListProperty, RemoteLoader, RemotePage}
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.request.{RequestContext, RequestHeaders}
import ui.core.state.{ListDataSource, ListProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

private abstract class VirtualListTestRoot extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      content
    }
}
