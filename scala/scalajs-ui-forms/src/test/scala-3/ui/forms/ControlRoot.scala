package ui.forms

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.forms.validators.NotBlankValidator

private abstract class ControlRoot extends AbstractComponent {
  val tagName = "div"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) { content }
}
