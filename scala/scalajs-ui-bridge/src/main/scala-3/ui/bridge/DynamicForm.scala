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

/** `form` -- the root of a dynamically bound form. Mirrors `ui.forms.Form`, minus the
  * `ClassDescriptor` it cannot have.
  */
private[bridge] final class DynamicForm(
    val formModel: js.Dictionary[js.Any],
    val formSchema: Map[String, Array[Annotation]],
    formName: String
) extends AbstractComponent,
      Editable,
      DynamicFormular {

  val tagName = "form"

  override def prefix: String = formName

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      FormContext.provide(this)
      addDisposable(editableProperty.observe { editable =>
        controls.foreach(_.editableProperty.set(editable))
      })
      on("submit")(_.preventDefault())
    }
}
