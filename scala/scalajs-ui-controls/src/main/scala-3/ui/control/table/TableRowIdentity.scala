package ui.control.table

import ui.core.state.ListDataSource

import scala.collection.mutable

/** Resolves stable row keys only against values that are already present in the source.
  *
  * Duplicate keys are deliberately unresolved: choosing either occurrence would silently move
  * selection or focus to a different entity. Sparse remote gaps are never fetched here.
  */
private[table] object TableRowIdentity {
  final case class Key(value: Any)

  def keyOf[S](item: S, rowKey: S => Any): Option[Key] =
    try Some(Key(rowKey(item)))
    catch case _: Throwable => None

  def locate[S](
      source: ListDataSource[S],
      rowKey: S => Any,
      requested: Set[Key]
  ): Map[Key, Int] = {
    if (requested.isEmpty) return Map.empty

    val positions = mutable.Map.from(requested.iterator.map(_ -> -1))
    var index     = 0
    try
      while (index < math.max(0, source.totalLength)) {
        source.itemAt(index).foreach { item =>
          val key = Key(rowKey(item))
          positions.get(key).foreach { previous =>
            positions.update(key, if (previous == -1) index else -2)
          }
        }
        index += 1
      }
      positions.iterator.collect { case (key, found) if found >= 0 => key -> found }.toMap
    catch case _: Throwable => Map.empty
  }
}
