package ui.core.document

import ui.core.render.{Cursor, DomHostElement, HostElement, SsrHostElement, SsrRawTextNode, SsrTextNode}
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** Reconciles `document.head` against the registered entries.
  *
  * Two rules make this survive a head that the application does not own alone -- the bundler's
  * script and stylesheet tags, whatever a Vite plugin injects in development:
  *
  *   - Nodes without the [[HeadSink.Marker]] are never touched.
  *   - A marked node is removed only once this sink has actually managed its key. A key that only
  *     ever arrived from the server -- the asset tags -- is left in place, which is what keeps
  *     hydration from tearing out the stylesheet it is running under.
  *
  * Position is not reconciled either. Nodes already in the head keep theirs, new ones are appended.
  * Order in the head carries no meaning except for `<meta charset>` and `<base>`, and those are
  * registered before the first render and therefore server-rendered in the right place.
  */
final class BrowserHeadSink(head: dom.Element, html: Option[dom.Element]) extends HeadSink {

  private val managedKeys   = mutable.Set.empty[String]
  private val ownAttributes = mutable.Set.empty[String]

  def update(entries: Seq[HeadEntry], htmlAttributes: Seq[(String, String)]): Unit = {
    val existing = markedNodes()
    val desired  = entries.map(_.key).toSet

    entries.foreach { entry =>
      managedKeys += entry.key

      val reusable =
        existing
          .get(entry.key)
          .filter(_.tagName.equalsIgnoreCase(entry.tagName))

      reusable match {
        case Some(element) =>
          sync(element, entry)

        case None =>
          existing.get(entry.key).foreach(head.removeChild)
          val element = dom.document.createElement(entry.tagName)
          sync(element, entry)
          head.appendChild(element)
      }
    }

    existing.foreach { case (key, element) =>
      if (!desired.contains(key) && managedKeys.contains(key)) head.removeChild(element)
    }

    html.foreach { element =>
      val next = htmlAttributes.map(_._1).toSet
      ownAttributes.diff(next).foreach(element.removeAttribute)
      ownAttributes.clear()
      htmlAttributes.foreach { case (name, value) =>
        if (element.getAttribute(name) != value) element.setAttribute(name, value)
        ownAttributes += name
      }
    }
  }

  private def markedNodes(): mutable.LinkedHashMap[String, dom.Element] = {
    val nodes  = head.querySelectorAll(s"[${HeadSink.Marker}]")
    val result = mutable.LinkedHashMap.empty[String, dom.Element]

    for (index <- 0 until nodes.length) {
      nodes(index) match {
        case element: dom.Element =>
          val key = element.getAttribute(HeadSink.Marker)
          if (key != null && !result.contains(key)) result(key) = element
        case _ => ()
      }
    }

    result
  }

  private def sync(element: dom.Element, entry: HeadEntry): Unit = {
    val desired = entry.attributes.toMap + (HeadSink.Marker -> entry.key)

    attributeNames(element).foreach { name =>
      if (!desired.contains(name)) element.removeAttribute(name)
    }

    desired.foreach { case (name, value) =>
      if (element.getAttribute(name) != value) element.setAttribute(name, value)
    }

    val text = entry.text.getOrElse("")
    // Assigning the same text again would be pointless work everywhere and harmful for a script:
    // the node stays, but the comparison is what keeps a re-render from rewriting it.
    if (element.textContent != text) element.textContent = text
  }

  private def attributeNames(element: dom.Element): Seq[String] =
    element
      .asInstanceOf[js.Dynamic]
      .getAttributeNames()
      .asInstanceOf[js.Array[String]]
      .toSeq
}
