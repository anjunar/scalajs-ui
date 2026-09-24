package ui.forms

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.on
import ui.core.render.Cursor
import ui.core.state.Property
import ui.forms.Form.FormContext

/** Native textarea with the same form binding and validation lifecycle as [[Input]]. */
final class TextAreaInput(val name: String, val standalone: Boolean = false)
    extends AbstractComponent,
      ValueControl[String],
      Placeholder {

  val tagName                         = "textarea"
  val valueProperty: Property[String] = Property("")

  override def compose(cursor: Cursor): Unit = {
    val content = cursor.claimTextAreaContent(valueProperty.get)
    if (cursor.isHydrating) valueProperty.set(content.value)

    render(this, cursor) {
      setAttribute("name", name)

      on("input") { _ =>
        if (editableProperty.get) {
          dirtyProperty.set(true)
          valueProperty.set(content.value)
        } else content.setValue(valueProperty.get)
      }
      on("focus")(_ => focusedProperty.set(true))
      on("blur") { _ =>
        focusedProperty.set(false)
        validate()
      }

      addDisposable(valueProperty.observe { value =>
        content.setValue(Option(value).getOrElse(""))
        validate()
      })
      addDisposable(editableProperty.observe { editable =>
        setProperty("readOnly", !editable)
        if (!editable) content.setValue(valueProperty.get)
      })
      addDisposable(validators.observe(_ => validate()))
      addDisposable(dirtyProperty.observe(_ => validate()))

      if (!standalone) {
        val controller = FormContext.inject.getOrElse(
          throw new IllegalStateException(
            s"TextAreaInput '$name' requires a Form or FieldSet context."
          )
        )
        controller.register(this)
        addDisposable(() => controller.unregister(this))
      }
    }
  }

  override protected def setPlaceholder(value: String): Unit =
    setAttribute("placeholder", Option(value).getOrElse(""))
}

object TextAreaInput {
  export Editable.{editable, editable_=, editableProperty}

  def textAreaInput(name: String, standalone: Boolean = false)(
      body: TextAreaInput ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): TextAreaInput =
    DslLayer.child(new TextAreaInput(name, standalone))(body)
}
