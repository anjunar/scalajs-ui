package ui.forms

import ui.core.render.{Cursor, HostElement, TextAreaContent, TextNode}

/** Like [[TestCursor]], but hydrating: controls adopt what the host already holds. */
private final class HydratingTestCursor(
    onCreate: TestHostElement => Unit,
    textAreaContent: TextAreaContent
) extends Cursor {
  override def isHydrating: Boolean = true

  override def claimElement(tag: String): HostElement = {
    val host = new TestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new TestTextNode(initial)

  override def claimTextAreaContent(initial: String): TextAreaContent = textAreaContent

  override def sub(host: HostElement): Cursor = this
}
