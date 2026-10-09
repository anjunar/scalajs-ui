package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.forms.ImageCropper.*
import ui.viewport.Viewport
import ui.viewport.Viewport.viewport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private abstract class CropperRoot extends AbstractComponent {
  override val tagName: String = "main"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      content
    }
}
