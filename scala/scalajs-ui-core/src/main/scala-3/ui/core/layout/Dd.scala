package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Dd extends AbstractComponent {
  val tagName = "dd"
}

object Dd {
  def dd(body: Dd ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Dd =
    DslLayer.child(new Dd()) {
      body
    }
}
