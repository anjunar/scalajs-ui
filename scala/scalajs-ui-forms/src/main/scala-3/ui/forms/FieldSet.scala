package ui.forms

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.render.Cursor
import ui.core.state.Property
import ui.forms.Form.FormContext

import scala.collection.mutable

/** Groups controls in a `<fieldset>` without adding a level to the model. The grouped controls
  * register with the enclosing form as well, which is what binds, validates, clears and resets
  * them; the group itself keeps them for editability and for error paths that name the group.
  */
class FieldSet(val name: String) extends AbstractComponent, Control[Unit], FormController {

  val tagName                           = "fieldset"
  val valueProperty: Property[Unit]     = Property(())
  private var contextPrefix: String     = name
  private var enclosing: FormController = null

  override def prefix: String = contextPrefix

  val fields = mutable.LinkedHashMap.empty[String, Control[?]]

  def register(field: Control[?]): Unit =
    if (!fields.get(field.name).exists(_ eq field)) {
      Option(enclosing).foreach(_.register(field))
      fields.put(field.name, field)
      field.editableProperty.set(editableProperty.get)
    }

  def unregister(field: Control[?]): Unit =
    fields.get(field.name).filter(_ eq field).foreach { _ =>
      fields.remove(field.name)
      Option(enclosing).foreach(_.unregister(field))
    }

  def clearErrors(): Unit = setErrors(Nil)

  def resetInteractionState(): Unit = {
    setDirty(false)
    setFocused(false)
    setErrors(Nil)
  }

  def validateBindings(): Seq[String] = Seq.empty

  def setErrorResponses(responses: Seq[ErrorResponse]): Unit = {
    val (own, addressed) = responses.partition(_.path.isEmpty)
    if (own.nonEmpty) setErrors(own.map(_.message))
    addressed
      .groupBy(_.path.head)
      .foreach { case (fieldName, fieldErrors) =>
        fields.get(fieldName).foreach(ErrorResponse.deliver(_, fieldErrors.map(_.withoutHead)))
      }
  }

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      val ctrl = FormContext.inject.getOrElse(
        throw new IllegalStateException(s"FieldSet '$name' requires a Form context.")
      )
      enclosing = ctrl
      ctrl.register(this)
      addDisposable(() => ctrl.unregister(this))
      contextPrefix = s"${ctrl.prefix}.$name"
      setProperty("disabled", !editableProperty.get)
      addDisposable(editableProperty.observe { editable =>
        setProperty("disabled", !editable)
        fields.values.foreach(_.editableProperty.set(editable))
      })
      FormContext.provide(this)
    }
  }

  override def toString = s"FieldSet($name, ${fields.mkString(", ")})"
}

object FieldSet {
  def fieldSet(
      name: String
  )(body: FieldSet ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): FieldSet =
    DslLayer.child(new FieldSet(name)) {
      body
    }
}
