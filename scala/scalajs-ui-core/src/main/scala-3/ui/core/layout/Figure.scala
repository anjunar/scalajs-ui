package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Figure extends AbstractComponent {
  val tagName = "figure"
}

object Figure {
  def figure(body: Figure ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Figure =
    DslLayer.child(new Figure()) {
      body
    }
}
