package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Main extends AbstractComponent {
  val tagName = "main"
}

object Main {
  def main(body: Main ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Main =
    DslLayer.child(new Main()) {
      body
    }
}
