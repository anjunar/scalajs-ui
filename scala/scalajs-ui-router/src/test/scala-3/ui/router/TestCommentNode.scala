package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.TextComponent.text
import ui.core.render.{CommentNode, Cursor, HostElement, HostNode, TextNode, VirtualRange}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
private final class TestCommentNode(val text: String) extends CommentNode {
  override def renderHtml(): String = s"<!--$text-->"
}
