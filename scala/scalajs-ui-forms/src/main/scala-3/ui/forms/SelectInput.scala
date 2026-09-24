package ui.forms

import scala.scalajs.js
import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.on
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.{Property, ReadOnlyProperty}
import ui.forms.Form.FormContext

/** One native select option; the label may change with the current locale. */
final case class SelectOption(value: String, label: ReadOnlyProperty[String])

/** Native single-select control participating in form binding and validation. */
final class SelectInput(
    val name: String,
    val options: Seq[SelectOption],
    val standalone: Boolean = false
) extends AbstractComponent,
      ValueControl[String] {

  val tagName                         = "select"
  val valueProperty: Property[String] = Property("")

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      setAttribute("name", name)
      options.foreach { choice =>
        val option = new AbstractComponent { val tagName = "option" }
        child(option) {
          option.setAttribute("value", choice.value)
          option.addDisposable(valueProperty.observe { value =>
            if (value == choice.value) option.setAttribute("selected", "selected")
            else option.removeAttribute("selected")
          })
          text(choice.label) {}
        }
      }

      def read(): Unit = {
        if (editableProperty.get) {
          dirtyProperty.set(true)
          val next = property[js.Any]("value")
            .filter(value => value != null && !js.isUndefined(value))
            .map(_.toString)
            .getOrElse("")
          valueProperty.set(next)
        } else setProperty("value", valueProperty.get)
      }
      on("input")(_ => read())
      on("change")(_ => read())
      on("focus")(_ => focusedProperty.set(true))
      on("blur") { _ =>
        focusedProperty.set(false)
        validate()
      }

      addDisposable(valueProperty.observe { value =>
        setProperty("value", Option(value).getOrElse(""))
        validate()
      })
      addDisposable(editableProperty.observe { editable =>
        setProperty("disabled", !editable)
        if (!editable) setProperty("value", valueProperty.get)
      })
      addDisposable(validators.observe(_ => validate()))
      addDisposable(dirtyProperty.observe(_ => validate()))

      if (!standalone) {
        val controller = FormContext.inject.getOrElse(
          throw new IllegalStateException(
            s"SelectInput '$name' requires a Form or FieldSet context."
          )
        )
        controller.register(this)
        addDisposable(() => controller.unregister(this))
      }
    }
}

object SelectInput {
  export Editable.{editable, editable_=, editableProperty}

  def selectInput(name: String, options: Seq[SelectOption], standalone: Boolean = false)(
      body: SelectInput ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): SelectInput =
    DslLayer.child(new SelectInput(name, options, standalone))(body)
}
