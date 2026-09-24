package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Label extends AbstractComponent {
  val tagName = "label"
}

object Label {
  def label(body: Label ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Label =
    DslLayer.child(new Label()) {
      body
    }
}
