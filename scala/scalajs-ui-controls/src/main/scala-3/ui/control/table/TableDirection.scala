package ui.control.table

/** Inline direction used by table layout, horizontal navigation and column gestures. */
enum TableDirection(val htmlValue: String) {
  case LeftToRight extends TableDirection("ltr")
  case RightToLeft extends TableDirection("rtl")
}
