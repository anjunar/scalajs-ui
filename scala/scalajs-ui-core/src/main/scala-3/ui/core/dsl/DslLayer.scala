package ui.core.dsl

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.Cursor

object DslLayer {

  def render(root: AbstractComponent, cursor: Cursor)(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    body(using root)(using cursor)

  def renderInto[A <: AbstractComponent](target: A)(
      body: A ?=> Cursor ?=> Unit
  ): Unit = {
    if (!target.isBound || target.isDisposed) {
      throw new IllegalStateException(
        s"Component '${target.getClass.getSimpleName}' has no mounted content cursor."
      )
    }
    // Not the raw composition cursor: once hydration has completed, that one has no server nodes
    // left to claim and the next claim faults. Runtime resolves the cursor for resumed work --
    // `fresh` after hydration, before the end anchor of a virtual component.
    body(using target)(using Runtime.contentCursor(target))
  }

  def child[A <: AbstractComponent](component: A)(
      body: A ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): A = {
    val (mounted, childCursor) =
      Runtime.mountWithCursor(component, cursor, Some(parent))

    try {
      body(using mounted)(using childCursor)
      mounted
    } catch {
      case error: Throwable =>
        Runtime.unmount(mounted)
        throw error
    }
  }
}
