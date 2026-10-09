package ui.core.dsl

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.StyleDsl.*
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private abstract class StyleRoot extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      content
    }
}
