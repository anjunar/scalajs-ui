package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Nav extends AbstractComponent {
  val tagName = "nav"
}

object Nav {
  def nav(body: Nav ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Nav =
    DslLayer.child(new Nav()) {
      body
    }
}
