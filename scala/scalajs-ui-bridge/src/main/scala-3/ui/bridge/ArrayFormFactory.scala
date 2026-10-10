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
private[bridge] object ArrayFormFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name         = ControlFactories.str(options("name"))
    val standalone   = options.get("standalone").map(ControlFactories.bool).getOrElse(false)
    val itemRenderer =
      options("itemRenderer").asInstanceOf[js.Function1[Int, js.Function1[ScopeHandleBridge, Unit]]]

    // Built with the renderer already in hand (`initialRenderer`), not via `controlRenderer_=`
    // after the fact -- see `ArrayForm`'s constructor and `FormFactories.arrayFormRenderer` for why
    // the two are not equivalent under SSR/hydration. `self` is assigned before `DslLayer.child`
    // calls `compose`, so the renderer thunk always sees the real instance once it runs.
    var self: ArrayForm[js.Any] = null
    val renderer                = FormFactories.arrayFormRenderer(name, () => self, itemRenderer)
    self = new ArrayForm[js.Any](name, standalone, Some(renderer))

    DslLayer.child(self) {
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}
