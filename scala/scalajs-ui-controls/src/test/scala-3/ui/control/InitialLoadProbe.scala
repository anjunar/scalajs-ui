package ui.control

import ui.control.virtualized.{FixedRowGeometry, ItemGeometry, VirtualizedCollection}
import ui.core.remote.{RemoteListProperty, RemoteLoader, RemotePage}
import ui.core.render.Cursor
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Promise}

/** Exercises the shared data-source lifecycle without requiring DOM layout. */
private final class InitialLoadProbe(source: RemoteListProperty[String, Unit])
    extends VirtualizedCollection[String](source) {
  override val tagName: String                  = "div"
  override protected val geometry: ItemGeometry =
    new FixedRowGeometry(rowHeight = () => 20.0, headerHeightValue = () => 0.0, overscanRows = 0)
  override protected def renderableCount: Int     = source.totalLength
  override protected def recomputeVisible(): Unit = ()
  override protected def handleLocalItemsChange(change: ListProperty.Change[String]): Unit = ()
  override def compose(cursor: Cursor): Unit                                               = ()

  def attach(initialHydration: Boolean): Unit = {
    browserRendering = true
    hydrating = initialHydration
    installItemObservers()
  }
}
