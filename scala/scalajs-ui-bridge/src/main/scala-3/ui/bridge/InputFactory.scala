package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.on
import ui.core.render.Cursor
import ui.core.state.{CompositeDisposable, Disposable as CoreDisposable, ListProperty as CoreListProperty, Property as CoreProperty}
import ui.forms.*
import ui.forms.Form.FormContext
import ui.forms.validators.{Validator, ValidatorFactory}
import org.scalajs.dom
import reflect.Annotation

import scala.collection.mutable
import scala.scalajs.js
private[bridge] object InputFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name       = ControlFactories.str(options("name"))
    val standalone = options.get("standalone").map(ControlFactories.bool).getOrElse(false)

    Input.input(name, standalone) {
      val self = summon[Input]
      options
        .get("type")
        .foreach(value => Input.inputType_=(ControlFactories.str(value))(using self))
      options.get("placeholder").foreach(value => self.placeholder(ControlFactories.strProp(value)))
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}
