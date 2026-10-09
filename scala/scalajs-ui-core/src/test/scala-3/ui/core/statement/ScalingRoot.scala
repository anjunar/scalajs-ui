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
private final class ScalingRoot(items: ListProperty[String]) extends AbstractComponent {
  override val tagName: String = "ul"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      DslLayer.child(
        new Foreach[String](items, (value, _) => div { text(value) {} })
      ) {}
    }
}
