package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Footer extends AbstractComponent {
  val tagName = "footer"
}

object Footer {
  def footer(body: Footer ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Footer =
    DslLayer.child(new Footer()) {
      body
    }
}
