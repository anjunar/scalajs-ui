package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Fieldset extends AbstractComponent {
  val tagName = "fieldset"
}

object Fieldset {
  def fieldset(body: Fieldset ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Fieldset =
    DslLayer.child(new Fieldset()) {
      body
    }
}
