package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import ui.core.state.Disposable
import ui.forms.Input.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

private final class TestHostElement(val tagName: String) extends HostElement {
  private val attributes = mutable.Map.empty[String, String]
  private val properties = mutable.Map.empty[String, Any]
  private val styles     = mutable.Map.empty[String, String]
  private val children   = mutable.ArrayBuffer.empty[HostNode]
  private val listeners  = mutable.Map.empty[String, UiEvent => Unit]

  override def setAttribute(name: String, value: String): Unit = attributes.update(name, value)
  override def removeAttribute(name: String): Unit             = attributes.remove(name)
  override def attribute(name: String): Option[String]         = attributes.get(name)
  override def setProperty(name: String, value: Any): Unit     = properties.update(name, value)
  override def property[T](name: String): Option[T] = properties.get(name).map(_.asInstanceOf[T])
  override def setStyle(name: String, value: String): Unit = styles.update(name, value)
  override def removeStyle(name: String): Unit             = styles.remove(name)
  override def style(name: String): Option[String]         = styles.get(name)
  override def setClassNames(names: Seq[String]): Unit = setAttribute("class", names.mkString(" "))
  override def insertChild(index: Int, child: HostNode): Unit = children.insert(index, child)
  override def insertBefore(child: HostNode, before: Option[HostNode]): Unit =
    before.flatMap(node =>
      children.indexOf(node) match {
        case -1    => None
        case index => Some(index)
      }
    ) match {
      case Some(index) => children.insert(index, child)
      case None        => children += child
    }
  override def removeChild(child: HostNode): Unit = children -= child
  override def clearChildren(): Unit              = children.clear()
  override def childCount: Int                    = children.length
  override def renderHtml(): String               = s"<$tagName></$tagName>"

  override def on(eventName: String)(handler: UiEvent => Unit): Disposable = {
    listeners.update(eventName, handler)
    Disposable(listeners.remove(eventName))
  }

  def fire(eventName: String, rawEvent: Any): Unit =
    listeners(eventName)(new UiEvent {
      override def raw: Any                = rawEvent
      override def preventDefault(): Unit  = ()
      override def stopPropagation(): Unit = ()
    })
}
