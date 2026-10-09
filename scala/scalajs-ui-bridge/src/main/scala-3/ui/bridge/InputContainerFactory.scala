package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.on
import ui.core.render.Cursor
import ui.core.state.{
  CompositeDisposable,
  Disposable => CoreDisposable,
  ListProperty => CoreListProperty,
  Property => CoreProperty
}
import ui.forms.*
import ui.forms.Form.FormContext
import ui.forms.validators.{Validator, ValidatorFactory}
import org.scalajs.dom
import reflect.Annotation

import scala.collection.mutable
import scala.scalajs.js
private[bridge] object InputContainerFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val label = ControlFactories.strProp(options("label"))

    InputContainer.inputContainer(label) {
      val self = summon[AbstractComponent].asInstanceOf[InputContainer]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}

