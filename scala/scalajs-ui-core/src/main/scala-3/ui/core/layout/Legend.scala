package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Legend extends AbstractComponent {
  val tagName = "legend"
}

object Legend {
  def legend(body: Legend ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Legend =
    DslLayer.child(new Legend()) {
      body
    }
}
