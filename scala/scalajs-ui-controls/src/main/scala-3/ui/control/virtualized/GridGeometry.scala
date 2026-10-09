package ui.control.virtualized

/** Fixed cell size in a grid with N columns -- the DataGrid model.
  *
  * Overscan is expressed in rows: complete grid rows are always made visible.
  *
  * itemHeight and gap are separate rather than a finished rowStep because total height counts gaps
  * between rows, not after the last one: `rows * itemHeight + (rows - 1) * gap`. rowStep would add
  * one gap too many.
  */
final class GridGeometry(
    columnCount: () => Int,
    itemHeight: () => Double,
    gap: () => Double,
    contentTopOffset: () => Double,
    overscanRows: () => Int
) extends ItemGeometry {

  private def effectiveColumns: Int    = math.max(1, columnCount())
  private def effectiveRowStep: Double = math.max(1.0, itemHeight() + gap())

  /** Number of grid rows for `total` items. */
  private def rowCountFor(total: Int): Int =
    if (total <= 0) 0 else math.ceil(total.toDouble / effectiveColumns).toInt

  override def headerOffset: Double = contentTopOffset()

  override def topForIndex(index: Int): Double =
    headerOffset + math.max(0, index) / effectiveColumns * effectiveRowStep

  override def indexForOffset(offset: Double): Int = {
    val row = math.max(0, math.floor(math.max(0.0, offset) / effectiveRowStep).toInt)
    row * effectiveColumns
  }

  override def contentHeight(total: Int): Double = {
    val rows = rowCountFor(total)
    if (rows <= 0) 0.0 else rows * itemHeight() + math.max(0, rows - 1) * gap()
  }

  override def visibleRange(
      total: Int,
      scrollTop: Double,
      viewportHeight: Double
  ): (Int, Int) = {
    val columns            = effectiveColumns
    val step               = effectiveRowStep
    val rows               = rowCountFor(total)
    val effectiveScrollTop = math.max(0.0, scrollTop - headerOffset)
    val firstVisibleRow    =
      math.min(math.max(0, rows - 1), math.floor(effectiveScrollTop / step).toInt)
    val visibleRows = math.ceil(math.max(1.0, viewportHeight) / step).toInt + 1
    val overscan    = math.max(0, overscanRows())
    val startRow    = math.max(0, firstVisibleRow - overscan)
    val endRow      = math.min(rows, firstVisibleRow + visibleRows + overscan)

    (math.min(total, startRow * columns), math.min(total, endRow * columns))
  }
}
