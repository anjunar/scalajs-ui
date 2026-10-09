package ui.control.table

/** A pluggable alternative to the seven built-in [[ColumnResizePolicy]] strategies (C05). Must
  * return a vector the same length as `request.columns`; anything else is rejected and the previous
  * widths are kept, matching the atomic-or-nothing contract the built-in policies already have.
  * Returned widths are still clamped to each column's own min/max, so a policy cannot violate
  * bounds by construction.
  */
type CustomColumnResizePolicy = ColumnResizeRequest => Vector[Double]
