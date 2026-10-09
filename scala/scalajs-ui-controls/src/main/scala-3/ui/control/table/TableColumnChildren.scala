package ui.control.table

import ui.core.state.ListProperty

/** Child list that keeps parent identity coherent before observers see a mutation. */
private[table] final class TableColumnChildren[S](parent: TableColumn[S, ?])
    extends ListProperty[TableColumn[S, ?]] {

  private var previous = Vector.empty[TableColumn[S, ?]]

  private def validate(candidate: Seq[TableColumn[S, ?]]): Unit = {
    Option(parent.tableViewProperty.get) match {
      case Some(table) => table.validateColumnChildren(parent, candidate)
      case None        => TableColumnTree.validate(Seq(parent), null, Some(parent -> candidate))
    }
  }

  override def notified(change: ListProperty.Change[TableColumn[S, ?]]): Unit = {
    val current = toVector
    previous.filterNot(current.contains).foreach(_.setParentColumn(parent, null))
    current.foreach(_.setParentColumn(null, parent))
    previous = current
    super.notified(change)
  }

  override def addOne(column: TableColumn[S, ?]): this.type = {
    validate(toVector :+ column)
    super.addOne(column)
  }

  override def insert(index: Int, column: TableColumn[S, ?]): Unit = {
    validate(toVector.patch(index, Seq(column), 0))
    super.insert(index, column)
  }

  override def insertAll(index: Int, columns: IterableOnce[TableColumn[S, ?]]): Unit = {
    val inserted = columns.iterator.toVector
    validate(toVector.patch(index, inserted, 0))
    super.insertAll(index, inserted)
  }

  override def update(index: Int, column: TableColumn[S, ?]): Unit = {
    validate(toVector.updated(index, column))
    super.update(index, column)
  }

  override def remove(index: Int): TableColumn[S, ?] = {
    validate(toVector.patch(index, Nil, 1))
    super.remove(index)
  }

  override def remove(index: Int, count: Int): Unit = {
    validate(toVector.patch(index, Nil, count))
    super.remove(index, count)
  }

  override def clear(): Unit = {
    validate(Seq.empty)
    super.clear()
  }

  override def setAll(columns: IterableOnce[TableColumn[S, ?]]): this.type = {
    val replacement = columns.iterator.toVector
    validate(replacement)
    super.setAll(replacement)
  }

  override def patchInPlace(
      from: Int,
      patch: IterableOnce[TableColumn[S, ?]],
      replaced: Int
  ): this.type = {
    val inserted = patch.iterator.toVector
    validate(toVector.patch(from, inserted, replaced))
    super.patchInPlace(from, inserted, replaced)
  }
}
