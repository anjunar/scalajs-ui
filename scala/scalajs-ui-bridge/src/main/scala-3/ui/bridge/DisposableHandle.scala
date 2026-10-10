package ui.bridge

import ui.core.state.{Disposable as CoreDisposable}

import scala.scalajs.js

/** The JS projection of `ui.core.state.Disposable`. Constructed on the Scala side, handed to
  * TypeScript; mirrors `contract.ts`'s `Disposable`.
  */
final class DisposableHandle(private[bridge] final val underlying: CoreDisposable)
    extends js.Object {
  def dispose(): Unit = underlying.dispose()
}
