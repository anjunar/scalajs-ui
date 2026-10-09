package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.layout.{Button, HBox, VBox}
import ui.core.render.Cursor

import scala.scalajs.js

/** `dsl.ts`'s `button(label, options, body)` folds `label` and `ButtonOptions` into one options
  * object (`{ label, ...options }`); this factory is the one place that takes it back apart.
  */
private[bridge] object ButtonFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val labelProperty = ReactiveBridge.asProperty[String](options.getOrElse("label", ""))

    Button.button(labelProperty) {
      val self        = summon[Button]
      val childCursor = summon[Cursor]

      options.get("type").foreach(value => self.buttonType(value.asInstanceOf[String]))
      options
        .get("disabled")
        .foreach(value => self.disabled = ReactiveBridge.asProperty[Boolean](value))

      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, childCursor))
    }
  }
}
