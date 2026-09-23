package ui.control.table

/** One column's resize constraints, as seen by a [[CustomColumnResizePolicy]]. A public mirror of
  * the internal `TableColumnLayout.Column` contract -- that object stays `private[table]`, since it
  * is an implementation detail shared by layout, pointer gestures and the imperative API, not a
  * type an application should construct.
  */
final case class ColumnResizeSpec(min: Double, max: Double, preferred: Double, resizable: Boolean)

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

/** A pluggable alternative to the seven built-in [[ColumnResizePolicy]] strategies (C05). Must
  * return a vector the same length as `request.columns`; anything else is rejected and the previous
  * widths are kept, matching the atomic-or-nothing contract the built-in policies already have.
  * Returned widths are still clamped to each column's own min/max, so a policy cannot violate
  * bounds by construction.
  */
type CustomColumnResizePolicy = ColumnResizeRequest => Vector[Double]
