package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

class Paragraph extends AbstractComponent {
  val tagName = "p"
}

object Paragraph {
  def paragraph(body: Paragraph ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Paragraph =
    DslLayer.child(new Paragraph()) {
      body
    }
}
