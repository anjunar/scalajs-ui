package ui.forms

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.on
import ui.core.render.Cursor
import ui.core.state.Property
import ui.forms.Form.FormContext

/** Native checkbox with a Boolean value, form registration and validation state. */
final class CheckboxInput(val name: String, val standalone: Boolean = false)
    extends AbstractComponent,
      ValueControl[Boolean] {

  val tagName                          = "input"
  val valueProperty: Property[Boolean] = Property(false)

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      setAttribute("type", "checkbox")
      setAttribute("name", name)
      on("change") { _ =>
        if (editableProperty.get) {
          dirtyProperty.set(true)
          valueProperty.set(property[Boolean]("checked").getOrElse(false))
        } else setProperty("checked", valueProperty.get)
      }
      on("focus")(_ => focusedProperty.set(true))
      on("blur") { _ =>
        focusedProperty.set(false)
        validate()
      }

      addDisposable(valueProperty.observe { value =>
        setProperty("checked", value)
        validate()
      })
      addDisposable(editableProperty.observe { editable =>
        setProperty("disabled", !editable)
        if (!editable) setProperty("checked", valueProperty.get)
      })
      addDisposable(validators.observe(_ => validate()))
      addDisposable(dirtyProperty.observe(_ => validate()))

      if (!standalone) {
        val controller = FormContext.inject.getOrElse(
          throw new IllegalStateException(
            s"CheckboxInput '$name' requires a Form or FieldSet context."
          )
        )
        controller.register(this)
        addDisposable(() => controller.unregister(this))
      }
    }
}

object CheckboxInput {
  export Editable.{editable, editable_=, editableProperty}

  def checkboxInput(name: String, standalone: Boolean = false)(
      body: CheckboxInput ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): CheckboxInput =
    DslLayer.child(new CheckboxInput(name, standalone))(body)
}
