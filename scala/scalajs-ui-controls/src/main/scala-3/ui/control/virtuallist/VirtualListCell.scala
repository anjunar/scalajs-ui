package ui.control.virtuallist

import ui.control.virtuallist.VirtualListView.VisibleSlot
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.StyleDsl.*
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.Disposable
import org.scalajs.dom

/** One mounted slot of a [[VirtualListView]].
  *
  * The cell owns its item and index for its whole life; its position is the list's. `top` follows
  * the list's geometry revision, so a measurement elsewhere moves this cell without recomposing it,
  * and the renderer's output -- focus, selection, scroll state inside the cell -- survives.
  */
private final class VirtualListCell[T](
    owner: VirtualListView[T],
    slot: VisibleSlot[T]
) extends AbstractComponent {
  override val tagName: String = "div"

  private[virtuallist] def index: Int = slot.index

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("ui-virtual-list-cell")
      if (slot.item.isEmpty) addClass("ui-virtual-list-cell-loading")
      setAttribute("data-item-index", slot.index.toString)
      style {
        position = "absolute"
        left = "0"
        top = owner.slotTopProperty(slot.index)
        width = "100%"
        minHeight = owner.slotMinHeightProperty
        boxSizing = "border-box"
      }

      owner.getRenderer(slot.item.orNull, slot.index)(using this)(using cursor)
    }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) {
      owner.registerCell(this)
      domElement.foreach { element =>
        var active  = true
        val measure = () => {
          if (active) {
            val height = heightOf(element)
            if (height > 0) owner.handleMeasuredHeight(slot.index, height)
          }
        }

        val frame    = dom.window.requestAnimationFrame(_ => measure())
        val observer = new dom.ResizeObserver((_, _) => measure())
        observer.observe(element)
        addDisposable(Disposable {
          active = false
          dom.window.cancelAnimationFrame(frame)
          observer.disconnect()
        })
      }
    }

  /** The current rendered height, for the list to read back after it discarded its measurements. */
  private[virtuallist] def measuredHeight: Option[Double] =
    domElement.map(heightOf).filter(_ > 0)

  private def heightOf(element: dom.html.Element): Double = element.offsetHeight.toDouble

  private def domElement: Option[dom.html.Element] =
    if (!isBound || isDisposed) None
    else
      host match {
        case domHost: DomHostElement =>
          domHost.node match {
            case element: dom.html.Element => Some(element)
            case _                         => None
          }
        case _ => None
      }
}

object VirtualListCell {
  private[virtuallist] def virtualListCell[T](
      owner: VirtualListView[T],
      slot: VisibleSlot[T]
  )(
      body: VirtualListCell[?] ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): VirtualListCell[T] =
    DslLayer.child(new VirtualListCell[T](owner, slot)) {
      body
    }
}
