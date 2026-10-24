package ui.core.remote

import scala.scalajs.js

final case class RemotePage[V, Query](
    items: Seq[V],
    offset: Option[Int] = None,
    nextQuery: Option[Query] = None,
    totalCount: Option[Int] = None,
    hasMore: Option[Boolean] = None
)

object RemotePage {

  def fromArray[V, Query](
      items: js.Array[V],
      offset: Option[Int] = None,
      nextQuery: Option[Query] = None,
      totalCount: Option[Int] = None,
      hasMore: Option[Boolean] = None
  ): RemotePage[V, Query] =
    RemotePage(items.toSeq, offset, nextQuery, totalCount, hasMore)
}
