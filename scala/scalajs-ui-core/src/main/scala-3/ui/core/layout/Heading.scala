package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

class Heading(level: Int) extends AbstractComponent {
  require(level >= 1 && level <= 6, s"Heading level must be between 1 and 6, was: $level")
  val tagName = s"h$level"
}

object Heading {
  def heading(level: Int)(body: Heading ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Heading =
    DslLayer.child(new Heading(level)) {
      body
    }
}
