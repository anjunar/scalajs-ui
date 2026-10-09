package ui.core.document

import ui.core.render.{
  Cursor,
  DomHostElement,
  HostElement,
  SsrHostElement,
  SsrRawTextNode,
  SsrTextNode
}
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** Writes the head into the server-side render tree.
  *
  * The tree is mutable up to serialization, so entries a component registers late -- a route loader
  * that finishes after `<head>` was composed -- still land in the right place. That is the whole
  * reason the head is a registry rather than a component tree: in document order the head is
  * finished long before the page that describes it.
  */
final class SsrHeadSink(head: SsrHostElement, html: Option[HostElement]) extends HeadSink {

  private val ownAttributes = mutable.Set.empty[String]

  def update(entries: Seq[HeadEntry], htmlAttributes: Seq[(String, String)]): Unit = {
    head.clearChildren()
    entries.foreach(entry => head.insertChild(head.childCount, nodeFor(entry)))

    html.foreach { element =>
      val next = htmlAttributes.map(_._1).toSet
      ownAttributes.diff(next).foreach(element.removeAttribute)
      ownAttributes.clear()
      htmlAttributes.foreach { case (name, value) =>
        element.setAttribute(name, value)
        ownAttributes += name
      }
    }
  }

  private def nodeFor(entry: HeadEntry): SsrHostElement = {
    val element = new SsrHostElement(entry.tagName)

    element.setAttribute(HeadSink.Marker, entry.key)
    entry.attributes.foreach { case (name, value) => element.setAttribute(name, value) }

    entry.text.foreach { value =>
      // Not an SsrTextNode: an empty one leaves a hydration anchor behind, and head text is never
      // claimed by a component. The escaping it would apply happens here instead.
      val serialized = if (entry.rawText) value else SsrTextNode.escape(value)
      element.insertChild(0, new SsrRawTextNode(serialized))
    }

    element
  }
}
