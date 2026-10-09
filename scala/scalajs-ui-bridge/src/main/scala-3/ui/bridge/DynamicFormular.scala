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

/** The dynamic counterpart of `ui.forms.Formular[M]`: same field bookkeeping, error grouping and
  * lifecycle, but a control's model property is found by name in a `js.Dictionary` instead of
  * through a `ClassDescriptor`, and its validators come from a JS-supplied schema instead of
  * annotations on a Scala case class. See the file-level doc comment for why this exists as a
  * sibling trait rather than a change to `Formular.scala`.
  */
private[bridge] trait DynamicFormular extends FormController { self: AbstractComponent & Editable =>

  def formModel: js.Dictionary[js.Any]

  /** A sub-form can remain mounted while its parent model value is null. */
  protected def hasModel: Boolean = true
  def formSchema: Map[String, Array[Annotation]]

  val controls: CoreListProperty[Control[?]] = CoreListProperty()

  private val fieldsByName      = mutable.LinkedHashMap.empty[String, Control[?]]
  private val bindingsByControl = mutable.Map.empty[Control[?], CoreDisposable]
  private val unboundControls   = mutable.LinkedHashMap.empty[String, String]

  override def register(control: Control[?]): Unit = {
    fieldsByName.get(control.name) match {
      case Some(current) if current eq control => return
      case Some(current)                       => unregister(current)
      case None                                => ()
    }

    fieldsByName.put(control.name, control)
    controls += control
    bindNow(control)
    control.editableProperty.set(self.editableProperty.get)
  }

  override def unregister(control: Control[?]): Unit =
    fieldsByName.get(control.name).filter(_ eq control).foreach { _ =>
      fieldsByName.remove(control.name)
      unboundControls.remove(control.name)
      bindingsByControl.remove(control).foreach(_.dispose())
      val index = controls.indexWhere(_ eq control)
      if (index >= 0) controls.remove(index)
    }

  /** Every control that could not find a model property, by name -> reason. Mirrors
    * `Formular.validateBindings`.
    */
  def validateBindings(): Seq[String] =
    unboundControls.values.toSeq ++ controls.toSeq.flatMap {
      case nested: FormController => nested.validateBindings()
      case _                      => Seq.empty
    }

  def validate(): Seq[String] = controls.toSeq.flatMap(_.validate(forceVisible = true))

  /** Rebinds every child against the current model dictionary. This matters for nested forms whose
    * parent Property replaces the whole model object.
    */
  protected def rebindModel(): Unit =
    controls.toSeq.foreach { control =>
      bindingsByControl.remove(control).foreach(_.dispose())
      bindNow(control)
    }

  override def clearErrors(): Unit =
    controls.foreach { control =>
      control.setErrors(Nil)
      control match {
        case nested: FormController => nested.clearErrors()
        case _                      => ()
      }
    }

  override def resetInteractionState(): Unit =
    controls.foreach { control =>
      control.setDirty(false)
      control.setFocused(false)
      control.setErrors(Nil)
      control match {
        case nested: FormController => nested.resetInteractionState()
        case _                      => ()
      }
    }

  def setErrorResponses(responses: Seq[ErrorResponse]): Unit =
    responses
      .filter(_.path.nonEmpty)
      .groupBy(_.path.head)
      .foreach { case (fieldName, errors) =>
        fieldsByName.get(fieldName).foreach {
          case nested: FormController => nested.setErrorResponses(errors.map(_.withoutHead))
          case control                => control.setErrors(errors.map(_.message))
        }
      }

  private def bindNow(control: Control[?]): Unit = {
    val annotations     = formSchema.getOrElse(control.name, Array.empty[Annotation])
    val rawValidators   = control.validators.asInstanceOf[CoreListProperty[Validator[Any]]]
    val addedValidators = ValidatorFactory.createValidators(annotations)
    addedValidators.foreach(rawValidators += _)

    val binding: CoreDisposable =
      if (!hasModel) {
        control match {
          case nested: DynamicSubForm => nested.clearModel()
          case _                      => clearControlValue(control)
        }
        unboundControls.remove(control.name)
        CoreDisposable.empty
      } else
        FormFactories.resolveModelProperty(formModel, control.name) match {
          case Some(source) =>
            (control, source, control.valueProperty) match {
              case (
                    _: ImageCropper,
                    s: CoreProperty[Any @unchecked],
                    t: CoreProperty[Any @unchecked]
                  ) =>
                unboundControls.remove(control.name)
                MediaCodec.subscribeBidirectional(
                  s.asInstanceOf[CoreProperty[js.Any]],
                  t.asInstanceOf[CoreProperty[Media]]
                )
              case (_, s: CoreProperty[Any @unchecked], t: CoreProperty[Any @unchecked]) =>
                unboundControls.remove(control.name)
                CoreProperty.subscribeBidirectional(s, t)
              case (_, s: CoreListProperty[Any @unchecked], t: CoreListProperty[Any @unchecked]) =>
                unboundControls.remove(control.name)
                CoreListProperty.subscribeBidirectional(s, t)
              case _ =>
                failBinding(
                  control,
                  s"model property '${control.name}' does not pair with the control's value type"
                )
                CoreDisposable.empty
            }
          case None =>
            failBinding(control, s"no property named '${control.name}' on the form model")
            CoreDisposable.empty
        }

    bindingsByControl.put(
      control,
      CoreDisposable {
        binding.dispose()
        addedValidators.foreach { validator =>
          val index = rawValidators.indexWhere(_ == validator)
          if (index >= 0) rawValidators.remove(index)
        }
      }
    )
  }

  private def failBinding(control: Control[?], reason: String): Unit = {
    val message = s"Form cannot bind control '${control.name}': $reason."
    unboundControls.put(control.name, message)
    dom.console.error(message)
  }

  private def clearControlValue(control: Control[?]): Unit =
    control.valueProperty match {
      case property: CoreListProperty[?] => property.clear()
      case property: CoreProperty[?]     => property.reset()
      case _                             => ()
    }
}
