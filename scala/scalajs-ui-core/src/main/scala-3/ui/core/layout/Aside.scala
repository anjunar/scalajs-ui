package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Aside extends AbstractComponent {
  val tagName = "aside"
}

object Aside {
  def aside(body: Aside ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Aside =
    DslLayer.child(new Aside()) {
      body
    }
}
