package ui.core.remote

import ui.core.state.ListProperty
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try
private final case class PreparedRequest(init: dom.RequestInit, cleanup: () => Unit)

private def prepareRequest(request: RestRequest): PreparedRequest =
  request.timeoutMillis match {
    case None =>
      PreparedRequest(request.toRequestInit, () => ())

    case Some(timeoutMillis) =>
      val controller                                  = new dom.AbortController()
      val forwardAbort: js.Function1[dom.Event, Unit] =
        _ => controller.abort()

      request.signal.foreach { signal =>
        if (signal.aborted) controller.abort()
        else signal.addEventListener("abort", forwardAbort)
      }

      val timeout = setTimeout(timeoutMillis.toDouble) {
        controller.abort()
      }

      PreparedRequest(
        request.toRequestInitWithSignal(Some(controller.signal)),
        () => {
          clearTimeout(timeout)
          request.signal.foreach(_.removeEventListener("abort", forwardAbort))
        }
      )
  }

private[remote] def normalizeQueryParams(params: Map[String, Any]): Seq[(String, String)] =
  params.toSeq.flatMap { case (key, value) =>
    expandQueryParamValue(value).map(stringValue => key -> stringValue)
  }

private[remote] def expandQueryParamValue(value: Any): Seq[String] =
  value match {
    case null =>
      Seq.empty
    case None =>
      Seq.empty
    case Some(inner) =>
      expandQueryParamValue(inner)
    case values: js.Array[?] =>
      values.toSeq.flatMap(expandQueryParamValue)
    case values: Iterable[?] =>
      values.toSeq.flatMap(expandQueryParamValue)
    case other =>
      Seq(other.toString)
  }

private[remote] def encodeURIComponent(value: String): String =
  js.URIUtils.encodeURIComponent(value)
