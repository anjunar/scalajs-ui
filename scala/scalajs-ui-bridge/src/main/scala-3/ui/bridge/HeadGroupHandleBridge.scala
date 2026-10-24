package ui.bridge

import ui.core.document.DocumentHead

import scala.scalajs.js

/** The JS projection of `DocumentHead.Handle`. Mirrors `contract.ts`'s `HeadGroupHandle`: a group
  * of head entries replaced as a whole on every [[set]], for an entry that changes without its
  * owning component going away.
  */
final class HeadGroupHandleBridge(private[bridge] final val underlying: DocumentHead.Handle)
    extends js.Object {

  /** `contract.ts` declares `set(...entries)` variadic, so the repeated parameter here is what
    * makes the two agree: on a non-native JS class it compiles to a JS rest parameter. A single
    * `js.Array` parameter would receive the *first entry object* instead and read it as an empty
    * sequence -- `set(title, description)` would then clear the group rather than fill it.
    */
  def set(entries: HeadEntryFacade*): Unit =
    underlying.set(entries.map(HeadEntryFacade.toScala)*)

  def clear(): Unit = underlying.clear()

  def dispose(): Unit = underlying.dispose()
}
