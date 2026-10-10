package ui.viewport

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.{classIf, classes}
import ui.core.dsl.DslLayer.{child, render, renderInto}
import ui.core.dsl.EventDsl
import ui.core.dsl.EventDsl.*
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Div
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.render.UiEvent
import ui.core.render.DomHostElement
import ui.core.state.Disposable
import org.scalajs.dom

import scala.scalajs.js

final class Window(conf: Viewport.WindowConf) extends AbstractComponent {
  val tagName = "div"

  private var containerHost: Div                      = _
  private var activeResizeCleanup: Option[() => Unit] = None
  private var activeDragCleanup: Option[() => Unit]   = None

  override def compose(cursor: Cursor): Unit = {
    given AbstractComponent = this

    render(this, cursor) {
      addClass("ui-window")
      if (conf.resizable && !conf.autoHeight) addClass("ui-window--resizable")
      if (!conf.mobileSheet) addClass("ui-window--floating")
      if (conf.autoHeight) addClass("ui-window--auto-height")
      classIf("is-hidden", conf.visible.map(!_))

      style {
        position = "absolute"
        left = conf.leftPx.map(px => s"${px.round}px")
        top = conf.topPx.map(px => s"${px.round}px")
        width = conf.widthProperty.map(px => s"${px.round}px")
        if (conf.autoHeight) height = "auto"
        else height = conf.heightProperty.map(px => s"${px.round}px")
        zIndex = conf.zIndex.map(_.toString)
      }

      onClick { _ =>
        Viewport.touchWindow(conf)
        conf.onClick.foreach(_(this))
      }

      div {
        classes = Seq("ui-window__surface")

        div {
          classes = Seq("ui-window__header")

          on("pointerdown") { event =>
            startDrag(event)
          }

          div {
            classes = Seq("ui-window__title")
            text(conf.title) {}
          }

          div {
            classes = Seq("ui-window__actions")

            button("close") {
              classes = Seq("material-icons", "ui-window__chrome-button")
              buttonType("button")

              onClick { event =>
                event.stopPropagation()
                conf.onClose.foreach(_(this))
                conf.closeBehavior match {
                  case Viewport.WindowCloseBehavior.Remove => Viewport.closeWindow(conf)
                  case Viewport.WindowCloseBehavior.Hide   => Viewport.hideWindow(conf)
                }
              }
            }
          }
        }

        containerHost = div {
          classes = Seq("ui-window__container")
        }
      }

      if (conf.resizable && !conf.autoHeight) {
        Seq(
          ("n", 0, -1),
          ("ne", 1, -1),
          ("e", 1, 0),
          ("se", 1, 1),
          ("s", 0, 1),
          ("sw", -1, 1),
          ("w", -1, 0),
          ("nw", -1, -1)
        ).foreach { case (name, horizontal, vertical) =>
          div {
            classes = Seq("ui-window__handle", s"ui-window__handle--$name")
            on("pointerdown") { event => startResize(event, horizontal, vertical) }
          }
        }
      }
    }

    renderInto(containerHost) {
      conf.body
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) {
      val reposition: js.Function1[dom.Event, Unit] = _ => keepInsideViewport()
      keepInsideViewport()
      dom.window.addEventListener("resize", reposition)
      val sizeObserver =
        if (conf.autoHeight) host match {
          case browser: DomHostElement =>
            browser.node match {
              case element: dom.HTMLElement =>
                val observer = new dom.ResizeObserver((_, _) => positionMeasured(element))
                observer.observe(element)
                positionMeasured(element)
                Some(observer)
              case _ => None
            }
          case _ => None
        }
        else None
      addDisposable(Disposable {
        dom.window.removeEventListener("resize", reposition)
        sizeObserver.foreach(_.disconnect())
        stopResize()
        stopDrag()
      })
    }

  private def positionMeasured(element: dom.HTMLElement): Unit =
    Viewport.positionMeasuredWindow(
      conf,
      element.offsetWidth.toDouble,
      element.offsetHeight.toDouble
    )

  private def viewportWidth: Double =
    host match {
      case browser: DomHostElement =>
        browser.node match {
          case element: dom.HTMLElement =>
            element.offsetParent match {
              case parent: dom.HTMLElement if parent.clientWidth > 0 => parent.clientWidth.toDouble
              case _ => dom.window.innerWidth.toDouble
            }
          case _ => dom.window.innerWidth.toDouble
        }
      case _ => dom.window.innerWidth.toDouble
    }

  private def keepInsideViewport(): Unit = {
    if (conf.placement == Viewport.WindowPlacement.Centered) {
      if (conf.autoHeight) host match {
        case browser: DomHostElement =>
          browser.node match {
            case element: dom.HTMLElement => positionMeasured(element)
            case _                        => ()
          }
        case _ => ()
      }
      else Viewport.constrainWindow(conf)
      return
    }
    val width   = math.min(conf.widthProperty.get, math.max(0.0, viewportWidth - 16.0))
    val maxLeft = math.max(8.0, viewportWidth - width - 8.0)
    conf.leftPx.set(conf.leftPx.get.max(8.0).min(maxLeft))
  }

  private def stopResize(): Unit = {
    val cleanup = activeResizeCleanup
    activeResizeCleanup = None
    cleanup.foreach(_())
  }

  private def stopDrag(): Unit = {
    val cleanup = activeDragCleanup
    activeDragCleanup = None
    cleanup.foreach(_())
  }

  private def startResize(event: UiEvent, horizontal: Int, vertical: Int): Unit =
    event.raw match {
      case pointer: dom.PointerEvent if pointer.button == 0 =>
        stopResize()
        event.preventDefault()
        event.stopPropagation()
        Viewport.touchWindow(conf)

        val startX      = pointer.clientX.toDouble
        val startY      = pointer.clientY.toDouble
        val startLeft   = conf.leftPx.get
        val startTop    = conf.topPx.get
        val startWidth  = conf.widthProperty.get.min(1600.0).min(viewportWidth - 16.0)
        val startHeight = conf.heightProperty.get.min(1024.0)
        val pointerId   = pointer.pointerId

        val moveListener: js.Function1[dom.PointerEvent, Unit] = next =>
          if (next.pointerId == pointerId) {
            val dx    = next.clientX.toDouble - startX
            val dy    = next.clientY.toDouble - startY
            val width = horizontal match {
              case -1 => (startWidth - dx).max(360.0).min(startLeft + startWidth - 8.0)
              case 1  => (startWidth + dx).max(360.0).min(viewportWidth - startLeft - 8.0)
              case _  => startWidth
            }
            val height = vertical match {
              case -1 => (startHeight - dy).max(360.0)
              case 1  => (startHeight + dy).max(360.0)
              case _  => startHeight
            }
            if (horizontal != 0) {
              conf.widthProperty.set(width)
              if (horizontal < 0) conf.leftPx.set(startLeft + startWidth - width)
            }
            if (vertical != 0) {
              conf.heightProperty.set(height)
              if (vertical < 0) conf.topPx.set(startTop + startHeight - height)
            }
          }

        val finishListener: js.Function1[dom.PointerEvent, Unit] = next =>
          if (next.pointerId == pointerId) stopResize()

        dom.window.addEventListener("pointermove", moveListener)
        dom.window.addEventListener("pointerup", finishListener)
        dom.window.addEventListener("pointercancel", finishListener)
        activeResizeCleanup = Some(() => {
          dom.window.removeEventListener("pointermove", moveListener)
          dom.window.removeEventListener("pointerup", finishListener)
          dom.window.removeEventListener("pointercancel", finishListener)
        })
      case _ => ()
    }

  private def startDrag(event: UiEvent): Unit =
    event.raw match {
      case pointer: dom.PointerEvent
          if pointer.button == 0 &&
            (pointer.target match {
              case element: dom.Element => element.closest("button") == null
              case _                    => true
            }) =>
        stopDrag()
        event.preventDefault()
        Viewport.touchWindow(conf)

        val startX      = pointer.clientX.toDouble
        val startY      = pointer.clientY.toDouble
        val initialLeft = conf.leftPx.get
        val initialTop  = conf.topPx.get
        val pointerId   = pointer.pointerId

        val moveListener: js.Function1[dom.PointerEvent, Unit] = next =>
          if (next.pointerId == pointerId) {
            conf.userPositioned = true
            conf.leftPx.set(initialLeft + next.clientX.toDouble - startX)
            conf.topPx.set(initialTop + next.clientY.toDouble - startY)
            if (conf.autoHeight) host match {
              case browser: DomHostElement =>
                browser.node match {
                  case element: dom.HTMLElement => positionMeasured(element)
                  case _                        => ()
                }
              case _ => ()
            }
            else Viewport.constrainWindow(conf)
          }

        val finishListener: js.Function1[dom.PointerEvent, Unit] = next =>
          if (next.pointerId == pointerId) stopDrag()

        dom.window.addEventListener("pointermove", moveListener)
        dom.window.addEventListener("pointerup", finishListener)
        dom.window.addEventListener("pointercancel", finishListener)
        activeDragCleanup = Some(() => {
          dom.window.removeEventListener("pointermove", moveListener)
          dom.window.removeEventListener("pointerup", finishListener)
          dom.window.removeEventListener("pointercancel", finishListener)
        })
      case _ =>
        ()
    }
}

object Window {
  def window(conf: Viewport.WindowConf)(using AbstractComponent, Cursor): Window =
    child(new Window(conf)) {}
}
