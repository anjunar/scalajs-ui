package ui.core.statement

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js
private final class StrangerComponent extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      text("fremd") {}
    }
}

