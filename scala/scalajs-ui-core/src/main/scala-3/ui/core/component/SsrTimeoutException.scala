package ui.core.component

import ui.core.async.AsyncRenderContext
import ui.core.di.Context
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, HostElement, SsrCursor, VirtualHost}
import ui.core.render.*

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.timers.{clearTimeout, setTimeout}

final class SsrTimeoutException(val timeoutMs: Int)
    extends RuntimeException(s"SSR render timed out after $timeoutMs ms") {
  val status: Int = 504
}
