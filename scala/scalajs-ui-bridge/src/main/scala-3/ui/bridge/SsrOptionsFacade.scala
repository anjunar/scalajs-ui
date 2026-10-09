package ui.bridge

import scala.scalajs.js

/** Mirrors `contract.ts`'s `SsrOptions`. Native: [[UiRuntimeBridge.renderToString]] only ever reads
  * one, never builds one.
  */
@js.native
trait SsrOptionsFacade extends js.Object {
  val requestHeaders: js.UndefOr[js.Dictionary[js.UndefOr[String | js.Array[String]]]] = js.native
  val timeoutMs: js.UndefOr[Double]                                                    = js.native
  val document: js.UndefOr[Boolean]                                                    = js.native
}
