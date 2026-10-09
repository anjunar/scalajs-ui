package ui.viewport

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, SsrCursor}
import ui.viewport.Viewport.viewport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
private final class ViewportRoot(body: AbstractComponent ?=> Cursor ?=> Unit)
    extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor)(body)
}
