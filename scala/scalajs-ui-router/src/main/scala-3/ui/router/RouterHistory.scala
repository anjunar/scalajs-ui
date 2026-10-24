package ui.router

import ui.core.state.Disposable
import org.scalajs.dom

import scala.scalajs.js

/** The browser's session history as far as the router drives it: the entries it writes, the
  * Back/Forward signal it answers, and the scroll position that goes with a new entry.
  *
  * Separated from the router so its history behaviour can be exercised without a browser.
  */
private[router] trait RouterHistory {

  def pushState(url: String): Unit

  def replaceState(url: String): Unit

  /** Registers for Back/Forward; the browser has already moved to the entry when it fires. */
  def onPopState(listener: () => Unit): Disposable

  /** Runs `body` once the browser has painted the page that the pending scroll belongs to. */
  def afterPaint(body: () => Unit): Unit

  /** Scrolls to the fragment, or to the top without one. False when the fragment's element is not
    * in the document yet.
    */
  def scrollTo(fragment: Option[String]): Boolean
}

private[router] object RouterHistory {

  /** `window.history`, `popstate`, and the window's scroll position. */
  object Browser extends RouterHistory {

    override def pushState(url: String): Unit =
      dom.window.history.pushState(null, "", url)

    override def replaceState(url: String): Unit =
      dom.window.history.replaceState(null, "", url)

    override def onPopState(listener: () => Unit): Disposable = {
      val handler: js.Function1[dom.Event, Unit] = _ => listener()

      dom.window.addEventListener("popstate", handler)
      Disposable(dom.window.removeEventListener("popstate", handler))
    }

    override def afterPaint(body: () => Unit): Unit =
      dom.window.requestAnimationFrame(_ => body())

    override def scrollTo(fragment: Option[String]): Boolean =
      fragment match {
        case Some(id) =>
          Option(dom.document.getElementById(id)).exists { target =>
            target.scrollIntoView()
            true
          }

        case None =>
          dom.window.scrollTo(0, 0)
          true
      }
  }
}
