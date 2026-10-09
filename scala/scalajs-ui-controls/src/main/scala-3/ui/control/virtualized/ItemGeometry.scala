package ui.control.virtualized

/** Where is item `index`, and which items are visible?
  *
  * This is the only real difference between TableView, DataGrid, and VirtualListView. Everything
  * else -- scroll state, measurement, remote integration, crawl state -- was the same logic three
  * times and now lives in [[VirtualizedCollection]] and [[CrawlableCollection]].
  *
  * The axis is two-dimensional, not merely "fixed versus measured height":
  *
  * {{{
  *                    Height               Columns   Overscan
  *   TableView        fixed or measured    1         overscanRows / estimated pixels
  *   DataGrid         fixed (itemHeight+gap) N       overscanRows (Property)
  *   VirtualListView  measured             1         overscanPx   (Property)
  * }}}
  *
  * Implementations may keep state -- [[MeasuredRowGeometry]] holds measured heights and their
  * prefix sums.
  *
  * See scala/scalajs-ui-controls/VIRTUALIZATION.md and CHANGE.md P3-1.
  */
trait ItemGeometry {

  /** Space above the first item, for example due to a header. */
  def headerOffset: Double

  /** Top edge of item `index`, including [[headerOffset]]. */
  def topForIndex(index: Int): Double

  /** Index of the item at scroll position `offset`, where `offset` has already been adjusted for
    * [[headerOffset]].
    */
  def indexForOffset(offset: Double): Int

  /** Total content height for `total` items, excluding [[headerOffset]]. */
  def contentHeight(total: Int): Double

  /** Visible range as `[start, end)`.
    *
    * Only the non-crawl case: the crawl branch is identical in all three controls and lives in
    * [[VirtualizedCollection.visibleRange]].
    */
  def visibleRange(total: Int, scrollTop: Double, viewportHeight: Double): (Int, Int)
}
