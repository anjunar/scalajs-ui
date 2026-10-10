package ui.core.remote

import ui.core.state.ListProperty
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try
final case class RemoteRequestException(url: String, status: Int, responseBody: String)
    extends RuntimeException(
      s"Request to $url failed with status $status${
          if (responseBody.nonEmpty) s": $responseBody" else ""
        }"
    )

private[remote] def fetchPage[V, Query](
    request: RestRequest,
    query: Query,
    decode: (js.Any, Query) => RemotePage[V, Query],
    executionContext: ExecutionContext
): Future[RemotePage[V, Query]] = {
  given ExecutionContext = executionContext

  val prepared = prepareRequest(request)

  Future
    .fromTry(Try(dom.fetch(request.urlWithQueryString, prepared.init).toFuture))
    .flatten
    .flatMap { response =>
      if (response.ok) {
        response.json().toFuture.map(json => decode(json, query))
      } else {
        response
          .text()
          .toFuture
          .flatMap(body =>
            Future.failed(
              RemoteRequestException(request.urlWithQueryString, response.status.toInt, body)
            )
          )
      }
    }
    .andThen { case _ => prepared.cleanup() }
}
