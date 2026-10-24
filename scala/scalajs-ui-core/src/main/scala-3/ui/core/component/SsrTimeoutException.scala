package ui.core.component

import scala.scalajs.js.annotation.JSExport

/** Raised when `Runtime.renderToStringAsync` exceeds its deadline.
  *
  * It crosses the JavaScript boundary as it is: `Future.toJSPromise` rejects with the Throwable
  * itself, and the SSR servers (`server/server.mjs`, `npm/scalajs-ui-demo/server.mjs`) read
  * `error.status === 504` on it to answer with a gateway timeout instead of a generic 500. Without
  * the exports the fields would be name-mangled and invisible from JavaScript.
  */
final class SsrTimeoutException(@JSExport val timeoutMs: Int)
    extends RuntimeException(s"SSR render timed out after $timeoutMs ms") {
  @JSExport val status: Int = 504
}
