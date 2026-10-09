package ui.core.render

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.state.Property
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
private final class TextRoot(message: Property[String]) extends AbstractComponent {
  val tagName = "main"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      div {
        text(message) {}
      }
    }
}
