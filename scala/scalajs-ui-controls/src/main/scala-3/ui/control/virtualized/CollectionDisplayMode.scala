package ui.control.virtualized

/** How a virtualized collection presents its items: a paged slice with a footer pager, or a
  * scrolling viewport. See [[VirtualizedCollection]].
  */
enum CollectionDisplayMode {
  case Paging, Scrolling
}
