package ui.router

import ui.core.render.{CommentNode, Cursor, HostElement, HostNode, TextNode, VirtualRange}

/** Cursor that claims to run in a browser whose location is the test history's URL.
  *
  * It renders into test nodes: what matters here is that the router takes the browser path --
  * history writes, popstate, scroll -- which it only does for a browser cursor.
  */
private final class BrowserTestCursor(history: TestRouterHistory) extends Cursor {

  override def supportsAnchors: Boolean = true
  override def isBrowser: Boolean       = true

  override def browserUrl: Option[String] = Some(history.url)

  override def claimElement(tag: String): HostElement = new TestHostElement(tag)

  override def claimText(initial: String): TextNode = new TestTextNode(initial)

  override def claimComment(text: String): CommentNode = new TestCommentNode(text)

  override def claimRange(label: String): VirtualRange =
    VirtualRange(new TestCommentNode(s"$label:start"), new TestCommentNode(s"$label:end"), this)

  override def sub(host: HostElement): Cursor = this

  override def before(node: HostNode): Cursor = this
}
