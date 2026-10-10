package ui.core.remote

import ui.core.state.ListProperty
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try
final case class RestRequest(
    url: String,
    method: String = "GET",
    queryParams: Map[String, Any] = Map.empty,
    headers: Map[String, String] = Map.empty,
    body: js.UndefOr[js.Any] = js.undefined,
    initOverrides: Map[String, js.Any] = Map.empty,
    signal: Option[dom.AbortSignal] = None,
    timeoutMillis: Option[Int] = None
) {

  require(timeoutMillis.forall(_ > 0), "RestRequest.timeoutMillis must be positive")

  def withQueryParam(name: String, value: Any): RestRequest =
    copy(queryParams = queryParams.updated(name, value))

  def withHeader(name: String, value: String): RestRequest =
    copy(headers = headers.updated(name, value))

  def urlWithQueryString: String = {
    val normalizedParams = normalizeQueryParams(queryParams)
    if (normalizedParams.isEmpty) {
      url
    } else {
      val separator   = if (url.contains("?")) "&" else "?"
      val queryString = normalizedParams
        .map { case (key, value) => s"${encodeURIComponent(key)}=${encodeURIComponent(value)}" }
        .mkString("&")
      s"$url$separator$queryString"
    }
  }

  def toRequestInit: dom.RequestInit =
    toRequestInitWithSignal(signal)

  private[remote] def toRequestInitWithSignal(
      effectiveSignal: Option[dom.AbortSignal]
  ): dom.RequestInit = {
    val init = js.Dynamic.literal(method = method)

    if (headers.nonEmpty) {
      init.updateDynamic("headers")(js.Dictionary(headers.toSeq*))
    }

    if (!js.isUndefined(body)) {
      init.updateDynamic("body")(body)
    }

    initOverrides.foreach { case (key, value) =>
      init.updateDynamic(key)(value.asInstanceOf[js.Any])
    }

    effectiveSignal.foreach(init.updateDynamic("signal")(_))

    init.asInstanceOf[dom.RequestInit]
  }
}
