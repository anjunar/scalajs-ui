package ui.control.table

/** The inputs to one custom-policy invocation.
  *
  * `columns`/`widths` describe every currently visible leaf column, in visible order. `target` is
  * `Some((indices, delta))` for a user/API resize: one index for an ordinary leaf resize, every
  * visible leaf of a group for a group resize (the handle belongs to the group as a whole, not to
  * any single leaf -- a policy that wants JavaFX's own single-recipient behavior can just resize
  * `indices.head`). `target` is `None` for a pure re-layout with no specific target -- a viewport
  * size change or a column list change, the same event [[TableColumnLayout.layout]] handles for the
  * built-in strategies.
  */
final case class ColumnResizeRequest(
    columns: Vector[ColumnResizeSpec],
    widths: Vector[Double],
    viewport: Double,
    target: Option[(Vector[Int], Double)]
)
