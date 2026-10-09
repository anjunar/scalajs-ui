package ui.control.table

import ui.core.state.ListProperty

/** Validate ownership before mutation: observers must never see an invalid column list. */
private[table] final class TableColumnList[S](table: TableView[S])
    extends ListProperty[TableColumn[S, ?]] {

  private def validate(candidate: Seq[TableColumn[S, ?]]): Unit = {
    require(!table.isDisposed, "Cannot change the columns of a disposed TableView")
    table.checkColumnMutation()
    table.validateRootColumns(candidate)
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
