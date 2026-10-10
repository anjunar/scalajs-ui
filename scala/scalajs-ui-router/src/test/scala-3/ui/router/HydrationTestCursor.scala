package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.TextComponent.text
import ui.core.render.{CommentNode, Cursor, HostElement, HostNode, TextNode, VirtualRange}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/** Cursor that simulates a running hydration and records which ranges were claimed and adopted.
  *
  * A real HydratingCursor needs a DOM, which the test environment lacks. For the relevant question
  * here -- does the router claim or adopt -- the record is sufficient.
  */
private final class HydrationTestCursor extends Cursor {

  val claimed = mutable.ArrayBuffer.empty[String]
  val adopted = mutable.ArrayBuffer.empty[String]
  val texts   = mutable.ArrayBuffer.empty[String]

  override def supportsAnchors: Boolean = true
  override def isBrowser: Boolean       = false
  override def isHydrating: Boolean     = true

  override def claimElement(tag: String): HostElement = new TestHostElement(tag)

  override def claimText(initial: String): TextNode = {
    texts += initial
    new TestTextNode(initial)
  }

  override def claimComment(text: String): CommentNode = new TestCommentNode(text)

  override def claimRange(label: String): VirtualRange = {
    claimed += label
    VirtualRange(new TestCommentNode(s"$label:start"), new TestCommentNode(s"$label:end"), this)
  }

  override def adoptRange(label: String): VirtualRange = {
    adopted += label
    VirtualRange(new TestCommentNode(s"$label:start"), new TestCommentNode(s"$label:end"), this)
  }

  override def sub(host: HostElement): Cursor = this

  override def before(node: HostNode): Cursor = this
}
