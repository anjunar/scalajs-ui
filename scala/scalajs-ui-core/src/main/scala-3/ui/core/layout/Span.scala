package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

class Span extends AbstractComponent {
  val tagName = "span"
}

object Span {
  def span(body: Span ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Span =
    DslLayer.child(new Span()) {
      body
    }
}
