package ui.control.virtualized

/** Measured heights, one column -- used by VirtualListView and optionally TableView.
  *
  * Holds measured heights and their prefix sums. What is not yet measured uses the estimate;
  * `prefixDirtyFrom` records where sums must be rebuilt so one measurement does not recalculate the
  * entire list.
  *
  * Beyond the measured range, values are extrapolated using the estimate -- `renderableCount` may
  * exceed the number of measured rows, for example due to tail-padding reserve while loading more.
  *
  * Overscan is expressed in pixels rather than rows: with variable heights, a row count is not a
  * meaningful measure.
  */
final class MeasuredRowGeometry(
    estimateHeight: () => Double,
    headerHeightValue: () => Double,
    overscanPx: () => Double
) extends ItemGeometry {

  private val heights = scala.collection.mutable.ArrayBuffer.empty[Double]
  private val prefix  = scala.collection.mutable.ArrayBuffer(0.0)

  private var prefixDirtyFrom = Int.MaxValue

  private def estimate: Double = estimateHeight()

  override def headerOffset: Double = headerHeightValue()

  def measuredCount: Int = heights.length

  def ensureSize(size: Int): Unit =
    while (heights.length < size) {
      heights += estimate
      prefix += prefix.last + estimate
    }

  def heightFor(index: Int): Double =
    heights.lift(index).getOrElse(estimate)

  /** Records a measured height and returns the difference from the previous value, or None when it
    * has not changed meaningfully.
    */
  def updateHeight(index: Int, newHeight: Double): Option[Double] =
    if (index < 0) None
    else {
      ensureSize(index + 1)
      val previous = heights(index)
      val delta    = newHeight - previous
      if (math.abs(delta) <= 0.5) None
      else {
        heights(index) = newHeight
        prefixDirtyFrom = math.min(prefixDirtyFrom, index + 1)
        Some(delta)
      }
    }

  def rebuildPrefixIfDirty(): Unit =
    if (prefixDirtyFrom != Int.MaxValue) {
      var index = math.max(1, math.min(prefixDirtyFrom, prefix.length - 1))
      while (index < prefix.length) {
        prefix(index) = prefix(index - 1) + heights(index - 1)
        index += 1
      }
      prefixDirtyFrom = Int.MaxValue
    }

  def clear(): Unit = {
    heights.clear()
    prefix.clear()
    prefix += 0.0
    prefixDirtyFrom = Int.MaxValue
  }

  /** Distance from the first item to the top edge of `index`, without the header. */
  def offsetFor(index: Int): Double = {
    val loaded = heights.length
    if (index <= loaded) prefix.lift(index).getOrElse(prefix.last)
    else prefix.last + (index - loaded) * estimate
  }

  override def topForIndex(index: Int): Double =
    headerOffset + offsetFor(math.max(0, index))

  override def indexForOffset(offset: Double): Int = {
    val normalized = math.max(0.0, offset)
    val loaded     = heights.length

    if (loaded == 0) math.floor(normalized / estimate).toInt
    else if (normalized >= prefix.last)
      loaded + math.floor((normalized - prefix.last) / estimate).toInt
    else {
      var low  = 0
      var high = loaded
      while (low < high) {
        val middle = (low + high) / 2
        if (prefix(middle + 1) <= normalized) low = middle + 1
        else high = middle
      }
      low
    }
  }

  override def contentHeight(total: Int): Double = {
    rebuildPrefixIfDirty()
    val measured       = math.min(total, heights.length)
    val measuredHeight = prefix.lift(measured).getOrElse(0.0)
    measuredHeight + math.max(0, total - measured) * estimate
  }

  /** Upper bound for the number of rows mounted simultaneously.
    *
    * Without it, a list whose rows are all much lower than the estimate could pull arbitrarily many
    * rows into the visible range.
    */
  private def maxSlotsForViewport(viewportHeight: Double): Int = {
    val minimum = math.max(12.0, math.min(estimate, math.max(estimate / 2.0, 1.0)))
    val area    = viewportHeight + 2 * math.max(0.0, overscanPx())
    math.min(600, math.max(32, math.ceil(area / minimum).toInt + 8))
  }

  override def visibleRange(
      total: Int,
      scrollTop: Double,
      viewportHeight: Double
  ): (Int, Int) = {
    val height          = math.max(1.0, viewportHeight)
    val overscan        = math.max(0.0, overscanPx())
    val effectiveScroll = math.max(0.0, scrollTop - headerOffset)
    val startOffset     = math.max(0.0, effectiveScroll - overscan)
    val endOffset       = effectiveScroll + height + overscan
    val start           = math.max(0, math.min(indexForOffset(startOffset), total - 1))
    val maximum         = maxSlotsForViewport(height)

    var index = start
    var top   = offsetFor(index)
    while (index < total && top < endOffset && index - start < maximum) {
      top += heightFor(index)
      index += 1
    }

    (start, math.max(start + 1, index).min(total))
  }
}
