package ui.router

import ui.core.state.Disposable

import scala.collection.mutable

/** A session history without a browser: records what the router writes and lets a test move through
  * the entries the way Back/Forward does.
  */
private final class TestRouterHistory(var url: String) extends RouterHistory {

  val writes: mutable.ArrayBuffer[String]          = mutable.ArrayBuffer.empty
  val scrolls: mutable.ArrayBuffer[Option[String]] = mutable.ArrayBuffer.empty

  private var popStateListener = Option.empty[() => Unit]

  override def pushState(next: String): Unit = {
    writes += s"push $next"
    url = next
  }

  override def replaceState(next: String): Unit = {
    writes += s"replace $next"
    url = next
  }

  override def onPopState(listener: () => Unit): Disposable = {
    popStateListener = Some(listener)
    Disposable { popStateListener = None }
  }

  override def afterPaint(body: () => Unit): Unit =
    body()

  override def scrollTo(fragment: Option[String]): Boolean = {
    scrolls += fragment
    true
  }

  /** Back or Forward: the browser has moved to `entry` and announces it. */
  def travel(entry: String): Unit = {
    url = entry
    popStateListener.foreach(_())
  }

  def listening: Boolean = popStateListener.nonEmpty
}
