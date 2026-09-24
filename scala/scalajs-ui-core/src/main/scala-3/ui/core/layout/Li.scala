package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Li extends AbstractComponent {
  val tagName = "li"
}

object Li {
  def li(body: Li ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Li =
    DslLayer.child(new Li()) {
      body
    }
}
