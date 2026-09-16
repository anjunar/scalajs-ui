package ui.control.table

/** A leaf column's own current position in the requested remote sort, for a custom
  * `sortIndicator` body (C09). `ascending`/`priority` are meaningless when `sorted` is false;
  * `priority` is one-based, matching the header's existing `data-sort-priority`/`aria-description`.
  */
final case class TableSortIndicatorState(sorted: Boolean, ascending: Boolean, priority: Int)
