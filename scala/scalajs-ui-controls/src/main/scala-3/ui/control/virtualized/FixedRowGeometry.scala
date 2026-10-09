package ui.control.virtualized

/** Fixed row height, one column -- the default TableView model.
  *
  * Overscan is expressed in rows.
  */
final class FixedRowGeometry(
    rowHeight: () => Double,
    headerHeightValue: () => Double,
    overscanRows: Int
) extends ItemGeometry {

  private def effectiveRowHeight: Double = math.max(1.0, rowHeight())

  override def headerOffset: Double = headerHeightValue()

  override def topForIndex(index: Int): Double =
    headerOffset + math.max(0, index) * effectiveRowHeight

  override def indexForOffset(offset: Double): Int =
    math.max(0, math.floor(math.max(0.0, offset) / effectiveRowHeight).toInt)

  override def contentHeight(total: Int): Double =
    math.max(0, total) * effectiveRowHeight

  override def visibleRange(
      total: Int,
      scrollTop: Double,
      viewportHeight: Double
  ): (Int, Int) = {
    val rowHeightValue     = effectiveRowHeight
    val effectiveScrollTop = math.max(0.0, scrollTop - headerOffset)
    val firstVisible = math.min(total - 1, math.floor(effectiveScrollTop / rowHeightValue).toInt)
    val visibleCount = math.ceil(math.max(1.0, viewportHeight) / rowHeightValue).toInt + 1

    (
      math.max(0, firstVisible - overscanRows),
      math.min(total, firstVisible + visibleCount + overscanRows)
    )
  }
}
