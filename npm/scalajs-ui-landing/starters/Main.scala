import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.EventDsl.onClick
import ui.core.dsl.DslLayer.render
import ui.core.layout.Button.button
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.{Cursor, DomCursor}
import ui.core.state.Property
import org.scalajs.dom

object Main {
  def main(args: Array[String]): Unit =
    Runtime.mount(new Counter, DomCursor.root(dom.document.getElementById("root")))
}
