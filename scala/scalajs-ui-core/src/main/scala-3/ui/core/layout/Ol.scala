package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Ol extends AbstractComponent {
  val tagName = "ol"
}

object Ol {
  def ol(body: Ol ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Ol =
    DslLayer.child(new Ol()) {
      body
    }
}
