package ui.control

import ui.control.datagrid.DataGrid
import ui.control.table.TableView
import ui.control.virtuallist.VirtualListView
import ui.control.virtualized.{CollectionDisplayMode, FixedRowGeometry, ItemGeometry, VirtualizedCollection}
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.scalajs.js.Array

/** Minimal VirtualizedCollection that observes only whether a measurement notifies its follow-ups.
  */
private final class MeasurementProbe(itemCount: Int = 0, crawlOffset: Int = 0)
    extends VirtualizedCollection[String](
      ListProperty(Array((0 until itemCount).map(_.toString)*))
    ) {

  override val tagName: String = "div"

  var notifications: Int        = 0
  var viewportWidthSeen: Double = 0.0

  override protected val geometry: ItemGeometry =
    new FixedRowGeometry(rowHeight = () => 20.0, headerHeightValue = () => 0.0, overscanRows = 0)

  override protected def renderableCount: Int     = dataSource.totalLength
  override protected def recomputeVisible(): Unit = ()
  override protected def handleLocalItemsChange(change: ListProperty.Change[String]): Unit = ()

  override protected def onViewportWidthMeasured(width: Double): Unit =
    viewportWidthSeen = width

  override protected def onViewportMeasured(): Unit = {
    notifications += 1
    if (hydrating) hydrating = false
  }

  def startHydrating(): Unit = hydrating = true
  def hydratingNow: Boolean  = hydrating

  override protected def crawlWindow: Option[(Int, Int)] =
    Option.when(crawlOffset > 0)(crawlOffset -> 5)

  override protected def scheduleViewportMeasure(): Unit = ()

  def initializeBrowserMode(cursor: Cursor, explicitPaging: Boolean = false): Unit = {
    browserRendering = true
    hydrating = cursor.isHydrating
    if (explicitPaging) configureDisplayMode(CollectionDisplayMode.Paging)
    enableDefaultBrowserScrolling(cursor)
  }

  def displayModeNow: CollectionDisplayMode = displayModeProperty.get
  def initialScrollIndexNow: Int            = initialScrollIndex

  override def compose(cursor: Cursor): Unit = ()
}
