package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Dt extends AbstractComponent {
  val tagName = "dt"
}

object Dt {
  def dt(body: Dt ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Dt =
    DslLayer.child(new Dt()) {
      body
    }
}
