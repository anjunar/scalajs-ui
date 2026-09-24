package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Ul extends AbstractComponent {
  val tagName = "ul"
}

object Ul {
  def ul(body: Ul ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Ul =
    DslLayer.child(new Ul()) {
      body
    }
}
