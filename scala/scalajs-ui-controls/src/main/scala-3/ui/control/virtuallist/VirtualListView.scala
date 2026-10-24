package ui.control.virtuallist

import ui.control.virtualized.{CollectionDisplayMode, CrawlableCollection, MeasuredRowGeometry, VirtualizedCollection}
import ui.core.component.AbstractComponent
import ui.core.remote.RemoteSort
import ui.core.dsl.ClassDsl.{addClass, classIf, classes}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Condition.when
import ui.core.layout.Div
import ui.core.layout.Div.div
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.*
import ui.core.statement.Foreach.foreach
import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final class VirtualListView[T] private (
    source: ListDataSource[T],
    configure: VirtualListView[T] ?=> Cursor ?=> Unit
) extends VirtualizedCollection[T](source),
      CrawlableCollection[T] {

  private given ExecutionContext = ExecutionContext.global

  override val tagName: String = "div"

  val estimateHeightProperty: Property[Double] = Property(44.0)
  val overscanPxProperty: Property[Double]     = Property(240.0)
  val headerRowsProperty: Property[Int]        = Property(0)

  /** The mounted window as absolute slots. A slot carries only its identity -- index and item -- so
    * a cell stays mounted while its item is unchanged. Its position is not part of the slot:
    * [[VirtualListCell]] binds `top` to [[itemStateRevisionProperty]], so a measurement moves the
    * cells below it without remounting them. Before this, the slot carried `top` and `height` and
    * the window was republished whole, which remounted every visible cell on every measurement.
    */
  private val visibleSlotsProperty = ListProperty[VirtualListView.VisibleSlot[T]]()
  private val headerHeightProperty = Property(0.0)

  /** Measured heights, one column. The same geometry can serve TableView's optional variable-row
    * mode; shared scrolling and loading remain in VirtualizedCollection and CrawlableCollection.
    */
  override protected val geometry: MeasuredRowGeometry =
    new MeasuredRowGeometry(
      estimateHeight = () => estimateHeight,
      headerHeightValue = () => headerHeight,
      overscanPx = () => overscanPxProperty.get
    )

  override protected def crawlControlName: String = "VirtualListView"
  override protected def crawlDefaultLimit: Int   = VirtualListView.defaultLimit
  override protected def pagingUrlKey: String     = crawlIdProperty.get.getOrElse("list")

  private var tailPaddingItems                                          = defaultTailPadding
  private var cellRendererBody: Option[VirtualListView.Renderer[T]]     = None
  private var headerBody: Option[AbstractComponent ?=> Cursor ?=> Unit] = None
  private var headerComponent: Div | Null                               = null
  private var compositionReady                                          = false
  private val mountedCells            = mutable.Map.empty[Int, VirtualListCell[T]]
  private var mountedCellMeasureFrame = 0

  def items: ListDataSource[T] = dataSource

  def setRenderer(renderer: VirtualListView.Renderer[T]): Unit = {
    cellRendererBody = Option(renderer)
    if (compositionReady) {
      // Mounted cells composed the previous renderer; only a remount picks up the new one.
      visibleSlotsProperty.clear()
      refreshItemState()
    }
  }

  def getRenderer: VirtualListView.Renderer[T] =
    cellRendererBody.getOrElse(VirtualListView.emptyRenderer[T])

  private[control] def setHeader(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    headerBody = Some(body)

  def refresh(): Unit = refreshItemState()

  def scrollTo(index: Int): Unit = {
    val total = renderableCount
    if (total > 0) {
      val clamped       = math.max(0, math.min(total - 1, index))
      val nextScrollTop = geometry.topForIndex(clamped)
      scrollTopProperty.set(nextScrollTop)
      domElement(viewportComponent).foreach(_.scrollTop = nextScrollTop)
    }
  }

  override def compose(cursor: Cursor): Unit = {
    browserRendering = cursor.isBrowser
    hydrating = cursor.isHydrating

    // Structural configuration must be complete before Foreach claims its
    // SSR or hydration range.
    configure(using this)(using cursor)
    initializeCrawlState()
    initializeUrlState()
    if (browserRendering && !isPaging && crawlState.offset > 0) {
      initialScrollIndex = crawlState.offset
      if (!hydrating) scrollTopProperty.set(topForIndex(crawlState.offset))
    }
    installObservers()

    DslLayer.render(this, cursor) {
      addClass("ui-virtual-list")
      resolvedCrawlId.foreach(setAttribute("id", _))
      classIf("ui-virtual-list-loading", remoteStateRevisionProperty.map(_ => remoteLoading))
      classIf("ui-virtual-list-error", remoteStateRevisionProperty.map(_ => remoteError.nonEmpty))
      style {
        display = "flex"
        flexDirection = "column"
        width = "100%"
        height = "100%"
        minWidth = "0"
        minHeight = "0"
        overflow = "hidden"
        position = "relative"
      }

      viewportComponent = div {
        classes = Seq("ui-virtual-list-viewport")
        style {
          width = "100%"
          flex = "1 1 auto"
          minHeight = "0"
          overflow = displayModeProperty.map {
            case CollectionDisplayMode.Paging    => "hidden"
            case CollectionDisplayMode.Scrolling => "auto"
          }
          position = "relative"
        }

        on("scroll") { event =>
          event.raw match {
            case raw: dom.Event =>
              raw.currentTarget match {
                case element: dom.html.Element => updateScrollState(element)
                case _                         => ()
              }
            case _ => ()
          }
        }

        div {
          classes = Seq("ui-virtual-list-content")
          style {
            width = "100%"
            minHeight = "100%"
          }

          headerComponent = div {
            classes = Seq("ui-virtual-list-header-slot")
            style {
              width = "100%"
              minHeight = itemStateRevisionProperty.map(_ => s"${declaredHeaderHeight}px")
              boxSizing = "border-box"
            }
            headerBody.foreach { body => body }
          }

          div {
            classes = Seq("ui-virtual-list-items-surface")
            style {
              position = "relative"
              width = "100%"
              minHeight = "100%"
              if (browserRendering) {
                height = itemStateRevisionProperty.map(_ => s"${contentHeight}px")
              }
            }

            foreach(visibleSlotsProperty) { slot =>
              VirtualListCell.virtualListCell[T](this, slot) {}
            }
          }

        }
      }

      renderPagingFooter("ui-virtual-list")
    }

    compositionReady = true
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (browserRendering) {
      initializeBrowserCrawlState()
      enableDefaultBrowserScrolling(cursor)
      scheduleViewportMeasure()
      observeHeaderHeight(headerComponent, headerHeightProperty)
      observeViewportSize()
      addDisposable(Disposable {
        if (mountedCellMeasureFrame != 0) dom.window.cancelAnimationFrame(mountedCellMeasureFrame)
        mountedCellMeasureFrame = 0
      })
    }

  private def installObservers(): Unit = {
    addDisposable(displayModeProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(pageSizeProperty.observeWithoutInitial { _ =>
      pageIndexProperty.set(0)
      refreshItemState()
    })
    addDisposable(pageIndexProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(scrollTopProperty.observeWithoutInitial { _ =>
      recomputeVisible()
      persistVisibleScrollOffset()
    })
    addDisposable(viewportHeightProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(overscanPxProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(headerRowsProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(prefetchItemsProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(crawlableProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(crawlIdProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(estimateHeightProperty.observeWithoutInitial { _ =>
      resetMeasurements()
      refreshItemState()
    })
    addDisposable(headerHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    installItemObservers()
  }

  /** Releasing hydration may move the surface from the server's absolute crawl slice to a layout
    * relative to the page. The cells follow the geometry revision, so the release publishes one.
    */
  override protected def onViewportMeasured(): Unit = {
    val releasingHydration = hydrating
    super.onViewportMeasured()
    if (releasingHydration && !hydrating) bumpItemState()
  }

  override protected def recomputeVisible(): Unit = {
    geometry.rebuildPrefixIfDirty()
    val total = displayItemCount

    if (total <= 0) visibleSlotsProperty.clear()
    else {
      val (start, end) = visibleRange(total)
      reconcileVisibleSlots(start, end)
      if (browserRendering && !hydrating && end > start) {
        if (isPaging) requestPageLoad(start, end)
        else requestLazyLoadIfNecessary(start, end)
      }
    }
  }

  /** Brings the mounted window to `[start, end)` with the fewest mounts: slots that left the window
    * are removed at either end, slots that stay keep their cell, and only the slots that entered
    * are inserted. A slot whose item was replaced at the same index gets a fresh cell, since the
    * renderer composed the previous item. Items compare by reference, not by equality: a source
    * mutation that puts an equal-but-different object at a slot is a replacement.
    */
  private def reconcileVisibleSlots(start: Int, end: Int): Unit = {
    val dropBefore = visibleSlotsProperty.iterator.takeWhile(_.index < start).length
    if (dropBefore > 0) visibleSlotsProperty.remove(0, dropBefore)
    val keepUntil = visibleSlotsProperty.iterator.takeWhile(_.index < end).length
    if (keepUntil < visibleSlotsProperty.length)
      visibleSlotsProperty.remove(keepUntil, visibleSlotsProperty.length - keepUntil)

    var position = 0
    (start until end).foreach { index =>
      val item = itemAt(index)
      if (position >= visibleSlotsProperty.length || visibleSlotsProperty(position).index != index)
        visibleSlotsProperty.insert(position, new VirtualListView.VisibleSlot(index, item))
      else if (!sameItem(visibleSlotsProperty(position).item, item))
        visibleSlotsProperty.update(position, new VirtualListView.VisibleSlot(index, item))
      position += 1
    }
  }

  private def sameItem(previous: Option[T], next: Option[T]): Boolean =
    (previous, next) match {
      case (Some(a), Some(b)) => a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]
      case (None, None)       => true
      case _                  => false
    }

  /** Structural changes shift absolute indices, so their measurements are discarded and the cells
    * that stay mounted are remeasured. An update replaces one slot's cell, which measures itself;
    * an append shifts nothing.
    */
  override protected def handleLocalItemsChange(change: ListProperty.Change[T]): Unit = {
    change match {
      case ListProperty.UpdateAt(_, _, _, _) | ListProperty.Add(_, _) => ()
      case _                                                          => resetMeasurements()
    }
    refreshItemState()
  }

  override protected def resetMeasurements(): Unit = {
    geometry.clear()
    tailPaddingItems = defaultTailPadding
    scheduleMountedCellMeasure()
  }

  /** Distance of slot `index` from the top of the items surface. In a paged window the surface
    * starts at the page, not at item 0.
    */
  private def slotTop(index: Int): Double = {
    geometry.rebuildPrefixIfDirty()
    if (pagedVisibleRange) geometry.offsetFor(index) - geometry.offsetFor(pageStart)
    else geometry.offsetFor(index)
  }

  private[virtuallist] def slotTopProperty(index: Int): ReadOnlyProperty[String] =
    itemStateRevisionProperty.map(_ => s"${slotTop(index)}px")

  /** A cell reserves the estimate, never its own measurement: a measured minimum would keep a cell
    * from shrinking again, since the resize that should report the smaller height could not happen.
    */
  private[virtuallist] def slotMinHeightProperty: ReadOnlyProperty[String] =
    estimateHeightProperty.map(_ => s"${estimateHeight}px")

  private[virtuallist] def registerCell(cell: VirtualListCell[T]): Unit = {
    val index = cell.index
    mountedCells.update(index, cell)
    cell.addDisposable(Disposable {
      if (mountedCells.get(index).contains(cell)) mountedCells.remove(index)
    })
  }

  /** Cells that stay mounted across a reset still know their height; the geometry has forgotten it.
    * One frame later they are read back in one batch, so one bump repositions everything.
    */
  private def scheduleMountedCellMeasure(): Unit =
    if (browserRendering && mountedCellMeasureFrame == 0) {
      mountedCellMeasureFrame = dom.window.requestAnimationFrame { _ =>
        mountedCellMeasureFrame = 0
        if (!isDisposed)
          applyMeasurements(mountedCells.toVector.flatMap { (index, cell) =>
            cell.measuredHeight.map(index -> _)
          })
      }
    }

  private[virtuallist] def handleMeasuredHeight(index: Int, height: Double): Unit =
    applyMeasurements(Vector(index -> height))

  /** Records measured heights and repositions the window once. Cells above the scroll anchor keep
    * the anchor in place by shifting the scroll position by their accumulated delta; the scroll
    * observer then recomputes the window.
    */
  private def applyMeasurements(measurements: Seq[(Int, Double)]): Unit =
    if (!isDisposed) {
      geometry.rebuildPrefixIfDirty()
      val anchorIndex =
        geometry.indexForOffset(math.max(0.0, scrollTopProperty.get - headerHeight))
      var anchorDelta = 0.0
      var changed     = false
      measurements.foreach { (index, height) =>
        if (height > 0)
          geometry.updateHeight(index, height).foreach { delta =>
            changed = true
            if (browserRendering && index < anchorIndex) anchorDelta += delta
          }
      }
      if (changed) {
        geometry.rebuildPrefixIfDirty()
        bumpItemState()
        val previous = scrollTopProperty.get
        val adjusted =
          if (math.abs(anchorDelta) > 0.5) math.max(0.0, previous + anchorDelta) else previous
        if (adjusted != previous) {
          scrollTopProperty.set(adjusted)
          domElement(viewportComponent).foreach(_.scrollTop = adjusted)
        } else recomputeVisible()
      }
    }

  private def knownItemCount: Option[Int] =
    currentRemoteItems match {
      case null   => Some(dataSource.totalLength)
      case remote => remote.totalCountProperty.get
    }

  /** Broader than the base default: while loading continues or the total count is unknown, the list
    * reserves space at the end so scrolling does not stop at a provisional end.
    */
  override protected def canStillGrow: Boolean =
    Option(currentRemoteItems).exists { remote =>
      remote.loadingProperty.get || remote.canLoadMore || remote.totalCountProperty.get.isEmpty
    }

  override protected def renderableCount: Int =
    knownItemCount.getOrElse {
      val loaded = Option(currentRemoteItems).fold(dataSource.totalLength)(_.loadedLength)
      if (canStillGrow) loaded + tailPaddingItems else loaded
    }

  private def contentHeight: Double =
    if (pagedVisibleRange) {
      val (start, end) = pageRange(displayItemCount)
      geometry.contentHeight(end) - geometry.offsetFor(start)
    } else geometry.contentHeight(renderableCount)

  private def estimateHeight: Double       = math.max(1.0, estimateHeightProperty.get)
  private def declaredHeaderHeight: Double =
    if (headerBody.nonEmpty && headerRowsProperty.get > 0)
      estimateHeight * headerRowsProperty.get
    else 0.0
  private def headerHeight: Double =
    if (headerBody.isEmpty) 0.0
    else math.max(declaredHeaderHeight, math.max(0.0, headerHeightProperty.get))
  private def defaultTailPadding: Int = math.max(1, prefetchItemsProperty.get) * 3

}

object VirtualListView {
  type Renderer[T] = (T | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit

  private val defaultLimit = 50

  /** Identity of a mounted slot. Deliberately not a case class: two slots are the same only when
    * they are the same slot, so the Foreach over the window never treats a new slot as the old one.
    */
  private[control] final class VisibleSlot[T](val index: Int, val item: Option[T])

  private def emptyRenderer[T]: Renderer[T] =
    (_: T | Null, _: Int) => (_: AbstractComponent) ?=> (_: Cursor) ?=> ()

  def virtualList[T](
      source: ListDataSource[T]
  )(
      body: VirtualListView[T] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): VirtualListView[T] =
    DslLayer.child(new VirtualListView[T](source, body)) {}

  def items[T](using list: VirtualListView[T]): ListDataSource[T] = list.items

  def estimateHeight(using list: VirtualListView[?]): Double = list.estimateHeightProperty.get
  def estimateHeight_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.estimateHeightProperty.set(math.max(1.0, value))

  def estimateHeightPx(using list: VirtualListView[?]): Double = list.estimateHeightProperty.get
  def estimateHeightPx_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.estimateHeightProperty.set(math.max(1.0, value))

  def overscanPx(using list: VirtualListView[?]): Double = list.overscanPxProperty.get
  def overscanPx_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.overscanPxProperty.set(math.max(0.0, value))

  def prefetchItems(using list: VirtualListView[?]): Int = list.prefetchItemsProperty.get
  def prefetchItems_=(value: Int)(using list: VirtualListView[?]): Unit =
    list.prefetchItemsProperty.set(math.max(1, value))

  def paging(using list: VirtualListView[?]): Boolean =
    list.displayModeProperty.get == CollectionDisplayMode.Paging
  def paging_=(value: Boolean)(using list: VirtualListView[?]): Unit =
    list.configureDisplayMode(
      if (value) CollectionDisplayMode.Paging else CollectionDisplayMode.Scrolling
    )

  def scrolling(using list: VirtualListView[?]): Boolean                = !paging
  def scrolling_=(value: Boolean)(using list: VirtualListView[?]): Unit = paging_=(!value)

  def pageSize(using list: VirtualListView[?]): Int                = list.pageSizeProperty.get
  def pageSize_=(value: Int)(using list: VirtualListView[?]): Unit =
    list.pageSizeProperty.set(math.max(1, value))

  def headerRows(using list: VirtualListView[?]): Int                = list.headerRowsProperty.get
  def headerRows_=(value: Int)(using list: VirtualListView[?]): Unit =
    list.headerRowsProperty.set(math.max(0, value))

  def crawlable(using list: VirtualListView[?]): Boolean                = list.crawlableProperty.get
  def crawlable_=(value: Boolean)(using list: VirtualListView[?]): Unit =
    list.crawlableProperty.set(value)

  def crawlId(using list: VirtualListView[?]): Option[String]        = list.crawlIdProperty.get
  def crawlId_=(value: String)(using list: VirtualListView[?]): Unit =
    list.crawlIdProperty.set(Option(value))

  def cellRenderer[T](using list: VirtualListView[T]): Renderer[T]                = list.getRenderer
  def cellRenderer_=[T](value: Renderer[T])(using list: VirtualListView[T]): Unit =
    list.setRenderer(value)

  def header[T](body: AbstractComponent ?=> Cursor ?=> Unit)(using list: VirtualListView[T]): Unit =
    list.setHeader(body)
}
