package ui.core.remote

import ui.core.state.ListProperty
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try

/** Remote paging: HTTP requests, pages, and sorting.
  *
  * It previously lived in ui.core.state beside Property and Disposable, placing HTTP paging,
  * sorting, and query semantics in the foundation and exposing them to every scalajs-ui-core
  * consumer. See CHANGE.md P2-5.
  */

/** Loads a page. The result is a Future -- Future is the framework's internal async model (see
  * ARCHITECTURE.md). js.Promise appears only at the JavaScript export boundary and in facades for
  * JavaScript libraries.
  *
  * Loaders targeting a JavaScript API should use [[RemoteLoader.fromPromise]].
  */
trait RemoteLoader[V, Query] {
  def load(query: Query): Future[RemotePage[V, Query]]
}

object RemoteLoader {

  def apply[V, Query](loadFn: Query => Future[RemotePage[V, Query]]): RemoteLoader[V, Query] =
    new RemoteLoader[V, Query] {
      override def load(query: Query): Future[RemotePage[V, Query]] =
        loadFn(query)
    }

  /** JavaScript boundary: adapts a Promise-based loader to the internal Future model. Exactly one
    * conversion at a named location.
    */
  def fromPromise[V, Query](
      loadFn: Query => js.Promise[RemotePage[V, Query]]
  ): RemoteLoader[V, Query] =
    RemoteLoader(query => loadFn(query).toFuture)

  def rest[V, Query](
      requestFor: Query => RestRequest,
      executionContext: ExecutionContext = ExecutionContext.global
  )(decode: (js.Any, Query) => RemotePage[V, Query]): RemoteLoader[V, Query] =
    RemoteLoader(query => fetchPage(requestFor(query), query, decode, executionContext))
}
