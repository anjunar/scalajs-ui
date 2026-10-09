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
private[bridge] object ComboBoxFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name       = ControlFactories.str(options("name"))
    val standalone = options.get("standalone").map(ControlFactories.bool).getOrElse(false)

    ComboBox.comboBox[js.Any](name, standalone) {
      val self = summon[ComboBox[js.Any]]

      options.get("items").foreach {
        case handle: ListPropertyHandle[?] =>
          val list = handle.underlyingList.asInstanceOf[CoreListProperty[js.Any]]
          self.itemsProperty.setAll(list.toSeq)
          self.addDisposable(list.observeChanges(_ => self.itemsProperty.setAll(list.toSeq)))
        case value =>
          self.itemsProperty.setAll(value.asInstanceOf[js.Array[js.Any]].toSeq)
      }

      options.get("placeholder").foreach(value => self.placeholder(ControlFactories.strProp(value)))
      options
        .get("multiSelect")
        .foreach(value => ComboBox.multiSelect_=(ControlFactories.bool(value))(using self))
      options.get("converter").foreach { value =>
        val convert = value.asInstanceOf[js.Function1[js.Any, String]]
        ComboBox.converter_=[js.Any](using self)(item => convert(item))
      }
      options.get("itemRenderer").foreach { value =>
        val render = value.asInstanceOf[
          js.Function2[
            js.Any,
            ReadOnlyPropertyHandle[Boolean],
            js.Function1[ScopeHandleBridge, Unit]
          ]
        ]
        ComboBox.itemRenderer[js.Any](using self) {
          (item, selected) => (p: AbstractComponent) ?=> (c: Cursor) ?=>
            render(item, new ReadOnlyPropertyHandle(selected))(new ScopeHandleBridge(p, c))
        }
      }
    }
  }
}

