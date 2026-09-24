package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

class Section extends AbstractComponent {
  val tagName = "section"
}

object Section {
  def section(body: Section ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Section =
    DslLayer.child(new Section()) {
      body
    }
}
