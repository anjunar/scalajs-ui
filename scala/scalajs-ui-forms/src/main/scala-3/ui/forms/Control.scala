package ui.forms

import ui.core.component.AbstractComponent
import ui.core.i18n.{I18n, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog, RuntimeMessage}
import ui.core.state.{Disposable, ListProperty, Property, ReadOnlyProperty}
import ui.forms.validators.Validator

trait Control[V] extends Editable { self: AbstractComponent =>

  val name: String

  val valueProperty: ReadOnlyProperty[V]

  def addDisposable(disposable: Disposable): Unit
  val focusedProperty: Property[Boolean]         = Property(false)
  val dirtyProperty: Property[Boolean]           = Property(false)
  val validators: ListProperty[Validator[V]]     = ListProperty()
  val errors: ListProperty[String]               = ListProperty()
  val invalidProperty: ReadOnlyProperty[Boolean] = errors.map(_.nonEmpty)

  private var errorMessages    = Vector.empty[RuntimeMessage]
  private var renderingErrors  = false
  private var observingLocale  = false
  private val fallbackResolver = I18nResolver(MessageCatalog.empty)

  // Keep the public string property compatible with direct writes and clear/reset calls.
  self.addDisposable(errors.observeWithoutInitial { values =>
    if (!renderingErrors) setErrors(values.toSeq)
  })

  def value: ReadOnlyProperty[V] = valueProperty

  def invalid: ReadOnlyProperty[Boolean] = invalidProperty

  def setDirty(value: Boolean): Unit = dirtyProperty.set(value)

  def setFocused(value: Boolean): Unit = focusedProperty.set(value)

  def setErrors(values: IterableOnce[String]): Unit =
    setErrorMessages(values.iterator.filter(_.nonEmpty).map(I18n.literal).toVector)

  private def setErrorMessages(messages: Vector[RuntimeMessage]): Unit = {
    errorMessages = messages
    if (!observingLocale) I18nRuntime.current(using self).foreach { runtime =>
      observingLocale = true
      self.addDisposable(runtime.locale.observeWithoutInitial(_ => renderErrors()))
    }
    renderErrors()
  }

  private def resolve(message: RuntimeMessage): String =
    I18nRuntime
      .current(using self)
      .map(_.resolveNow(message))
      .getOrElse(fallbackResolver.resolve(message, I18nLocale.En))

  private def renderErrors(): Unit = {
    renderingErrors = true
    try errors.setAll(errorMessages.map(resolve))
    finally renderingErrors = false
  }

  def validate(forceVisible: Boolean = false): Seq[String] = {
    val validationErrors =
      if (editableProperty.get)
        validators.iterator.flatMap(_.validateMessage(valueProperty.get)).toVector
      else Vector.empty

    if (forceVisible || dirtyProperty.get) {
      if (forceVisible) dirtyProperty.set(true)
      setErrorMessages(validationErrors)
    } else {
      errors.clear()
    }

    validationErrors.map(resolve)
  }
}
