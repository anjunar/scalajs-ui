package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Header extends AbstractComponent {
  val tagName = "header"
}

object Header {
  def header(body: Header ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Header =
    DslLayer.child(new Header()) {
      body
    }
}
