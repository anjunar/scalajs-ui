package ui.control.table

/** One column's resize constraints, as seen by a [[CustomColumnResizePolicy]]. A public mirror of
  * the internal `TableColumnLayout.Column` contract -- that object stays `private[table]`, since it
  * is an implementation detail shared by layout, pointer gestures and the imperative API, not a
  * type an application should construct.
  */
final case class ColumnResizeSpec(min: Double, max: Double, preferred: Double, resizable: Boolean)
