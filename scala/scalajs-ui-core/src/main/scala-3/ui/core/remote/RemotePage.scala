package ui.core.remote

import ui.core.state.ListProperty
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try
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
