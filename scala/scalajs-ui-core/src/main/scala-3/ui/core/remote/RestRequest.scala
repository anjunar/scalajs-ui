package ui.core.remote

import org.scalajs.dom

import scala.scalajs.js

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
      // The query belongs before a fragment, and a fragment is never part of the query.
      val (base, fragment) = url.indexOf('#') match {
        case -1    => (url, "")
        case index => (url.substring(0, index), url.substring(index))
      }
      val separator =
        if (!base.contains("?")) "?"
        else if (base.endsWith("?") || base.endsWith("&")) ""
        else "&"
      val queryString = normalizedParams
        .map { case (key, value) => s"${encodeURIComponent(key)}=${encodeURIComponent(value)}" }
        .mkString("&")
      s"$base$separator$queryString$fragment"
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
