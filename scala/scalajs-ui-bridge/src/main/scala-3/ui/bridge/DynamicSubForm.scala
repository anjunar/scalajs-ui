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

/** `sub-form` -- a nested, dynamically bound `<fieldset>` that is itself a `Control` of its parent
  * form, the same relationship `ui.forms.SubForm` has to `Form`. Bound once, at registration, to
  * whatever the parent model held under this name -- matching `Formular`'s own per-control bind,
  * which never re-resolves after the first successful bind either. `newInstance`/`clearForm` are
  * not projected (see the file-level doc comment); a JS consumer wanting a fresh nested model
  * mounts a new `subForm` under `when()` instead.
  */
private[bridge] final class DynamicSubForm(
    val name: String,
    initialModel: js.Dictionary[js.Any],
    val formSchema: Map[String, Array[Annotation]],
    standalone: Boolean
) extends AbstractComponent,
      Control[js.Dictionary[js.Any]],
      Editable,
      DynamicFormular {

  val tagName = "fieldset"

  override val valueProperty: CoreProperty[js.Dictionary[js.Any]] = CoreProperty(initialModel)

  private var contextPrefix: String = name

  override def prefix: String = contextPrefix

  override def formModel: js.Dictionary[js.Any] =
    Option(valueProperty.get).getOrElse(js.Dictionary())

  override protected def hasModel: Boolean = valueProperty.get != null

  private[bridge] def clearModel(): Unit =
    if (valueProperty.get != null) valueProperty.set(null)

  override def validate(forceVisible: Boolean = false): Seq[String] =
    super.validate(forceVisible) ++ controls.toSeq.flatMap(_.validate(forceVisible))

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      if (!standalone) {
        val parentController = FormContext.inject.getOrElse(
          throw new IllegalStateException(s"subForm '$name' requires a Form context.")
        )
        parentController.register(this)
        contextPrefix = s"${parentController.prefix}.$name"
        addDisposable(() => parentController.unregister(this))
      }

      setProperty("disabled", !editableProperty.get)
      addDisposable(editableProperty.observe { editable =>
        setProperty("disabled", !editable)
        controls.foreach(_.editableProperty.set(editable))
      })

      // The parent form's bidirectional binding can replace valueProperty with
      // a completely new dictionary. Re-resolve child fields on each change so
      // controls never remain attached to the old nested model.
      addDisposable(valueProperty.observeWithoutInitial { _ => rebindModel() })

      FormContext.provide(this)
    }
}
