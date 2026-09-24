package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class FigCaption extends AbstractComponent {
  val tagName = "figcaption"
}

object FigCaption {
  def figcaption(
      body: FigCaption ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): FigCaption =
    DslLayer.child(new FigCaption()) {
      body
    }
}
