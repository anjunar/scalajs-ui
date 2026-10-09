package ui.bridge

import ui.core.document.{DocumentHead, HeadEntry}

import scala.scalajs.js

/** The JS projection of `ui.core.document.DocumentHead`. Mirrors `contract.ts`'s
  * `DocumentHeadHandle`.
  */
final class DocumentHeadHandleBridge(private[bridge] final val underlying: DocumentHead)
    extends js.Object {

  def push(entry: HeadEntryFacade): DisposableHandle =
    new DisposableHandle(underlying.push(HeadEntryFacade.toScala(entry)))

  def htmlAttribute(name: String, value: String): Unit =
    underlying.htmlAttribute(name, value)

  def removeHtmlAttribute(name: String): Unit =
    underlying.removeHtmlAttribute(name)

  def handle(): HeadGroupHandleBridge =
    new HeadGroupHandleBridge(underlying.handle())
}
