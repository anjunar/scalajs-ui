package ui.core.document

import ui.core.render.{Cursor, DomHostElement, HostElement, SsrHostElement, SsrRawTextNode, SsrTextNode}
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** Where a [[DocumentHead]] writes what components registered.
  *
  * Two of them exist: the server writes into the `<head>` element of the SSR tree, the browser
  * reconciles the real `document.head`. Splitting them here is what lets the same registry serve
  * both without the components knowing which side they run on.
  */
trait HeadSink {
  def update(entries: Seq[HeadEntry], htmlAttributes: Seq[(String, String)]): Unit
}

object HeadSink {

  /** The attribute that marks a node in the head as belonging to a [[DocumentHead]] entry. It
    * carries the entry's key, which is how the browser sink finds a server-rendered node again.
    */
  val Marker: String = "data-ui-head"

  /** Before a [[ui.core.layout.Head]] connected one. A [[DocumentHead]] is usable without a head
    * element -- a component test, a render that only asks for the entries -- and then simply has
    * nowhere to write.
    */
  val Discarding: HeadSink = (_, _) => ()

  /** The sink matching `cursor`, writing into the given `<head>` and `<html>` hosts. */
  def apply(cursor: Cursor, head: HostElement, html: Option[HostElement]): HeadSink =
    (head, html) match {
      case (browserHead: DomHostElement, browserHtml) if cursor.isBrowser =>
        new BrowserHeadSink(
          browserHead.node,
          browserHtml.collect { case element: DomHostElement => element.node }
        )

      case (ssrHead: SsrHostElement, ssrHtml) =>
        new SsrHeadSink(ssrHead, ssrHtml)

      case (other, _) =>
        throw new IllegalArgumentException(
          s"No head sink for host ${other.getClass.getName} (browser=${cursor.isBrowser})."
        )
    }
}
