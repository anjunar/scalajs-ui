package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Dl extends AbstractComponent {
  val tagName = "dl"
}

object Dl {
  def dl(body: Dl ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Dl =
    DslLayer.child(new Dl()) {
      body
    }
}
