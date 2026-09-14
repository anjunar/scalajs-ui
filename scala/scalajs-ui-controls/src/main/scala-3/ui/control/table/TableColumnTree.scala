package ui.control.table

import scala.collection.mutable

/** One validated column forest is the source for ownership, headers, layout and cells. */
private[table] object TableColumnTree {
  final case class Node[S](column: TableColumn[S, ?], parent: TableColumn[S, ?] | Null, depth: Int)

  def nodes[S](
      roots: Seq[TableColumn[S, ?]],
      replacement: Option[(TableColumn[S, ?], Seq[TableColumn[S, ?]])] = None
  ): Vector[Node[S]] = {
    val result = Vector.newBuilder[Node[S]]
    def visit(column: TableColumn[S, ?], parent: TableColumn[S, ?] | Null, depth: Int): Unit = {
      result += Node(column, parent, depth)
      val children = replacement
        .collect { case (`column`, values) => values }
        .getOrElse(column.columns.toVector)
      children.foreach(visit(_, column, depth + 1))
    }
    roots.foreach(visit(_, null, 0))
    result.result()
  }

  def validate[S](
      roots: Seq[TableColumn[S, ?]],
      table: TableView[S] | Null,
      replacement: Option[(TableColumn[S, ?], Seq[TableColumn[S, ?]])] = None
  ): Unit = {
    val seen = mutable.HashSet.empty[TableColumn[S, ?]]
    def visit(column: TableColumn[S, ?], parent: TableColumn[S, ?] | Null): Unit = {
      require(column != null && !column.isDisposed, "Cannot attach a null or disposed TableColumn")
      require(seen.add(column), "A TableColumn may occur only once in a table column tree")
      val currentParent = column.parentColumnProperty.get
      require(
        currentParent == null || (currentParent eq parent),
        "A TableColumn cannot belong to two parent columns"
      )
      val owner = column.tableViewProperty.get
      require(
        owner == null || (table != null && (owner eq table)),
        "A TableColumn cannot belong to two TableViews"
      )
      val children = replacement
        .collect { case (`column`, values) => values }
        .getOrElse(column.columns.toVector)
      children.foreach(visit(_, column))
    }
    roots.foreach(visit(_, null))
  }

  def visibleLeaves[S](roots: Seq[TableColumn[S, ?]]): Vector[TableColumn[S, ?]] = {
    val result = Vector.newBuilder[TableColumn[S, ?]]
    def visit(column: TableColumn[S, ?], ancestorsVisible: Boolean): Unit = {
      val visible = ancestorsVisible && column.visible
      if (visible) {
        val children = column.columns.toVector
        if (children.isEmpty) result += column
        else children.foreach(visit(_, visible))
      }
    }
    roots.foreach(visit(_, true))
    result.result()
  }

  def leaves[S](roots: Seq[TableColumn[S, ?]]): Vector[TableColumn[S, ?]] = {
    val result                                 = Vector.newBuilder[TableColumn[S, ?]]
    def visit(column: TableColumn[S, ?]): Unit = {
      val children = column.columns.toVector
      if (children.isEmpty) result += column else children.foreach(visit)
    }
    roots.foreach(visit)
    result.result()
  }
}
