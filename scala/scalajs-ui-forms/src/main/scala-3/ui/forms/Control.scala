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

  // Two sources feed `errors`: the control's own validation, and messages handed in from outside
  // through `setErrors` -- typically a server response. They are kept apart so that validating an
  // untouched control can hide validator messages without discarding what the server said.
  private var validatorMessages                     = Vector.empty[RuntimeMessage]
  private var externalMessages                      = Vector.empty[RuntimeMessage]
  private var externalMessagesUntilEdit: Disposable = Disposable.empty
  private var renderingErrors                       = false
  private var observingLocale                       = false
  private val fallbackResolver                      = I18nResolver(MessageCatalog.empty)

  // Keep the public string property compatible with direct writes and clear/reset calls.
  self.addDisposable(errors.observeWithoutInitial { values =>
    if (!renderingErrors) setErrors(values.toSeq)
  })

  def value: ReadOnlyProperty[V] = valueProperty

  def invalid: ReadOnlyProperty[Boolean] = invalidProperty

  def setDirty(value: Boolean): Unit = dirtyProperty.set(value)

  def setFocused(value: Boolean): Unit = focusedProperty.set(value)

  /** Replaces every displayed error with `values`. They stay until the control is cleared or its
    * value changes; validating the control in between leaves them in place.
    */
  def setErrors(values: IterableOnce[String]): Unit = {
    validatorMessages = Vector.empty
    externalMessages = values.iterator.filter(_.nonEmpty).map(I18n.literal).toVector
    externalMessagesUntilEdit.dispose()
    externalMessagesUntilEdit =
      if (externalMessages.isEmpty) Disposable.empty
      else
        valueProperty.observeWithoutInitial { _ =>
          externalMessagesUntilEdit.dispose()
          externalMessagesUntilEdit = Disposable.empty
          externalMessages = Vector.empty
          renderErrors()
        }
    renderErrors()
  }

  /** The messages the control's validation produces for its current value. A control with checks
    * beyond its `validators` extends this, so that every message takes the same translatable route.
    */
  protected def validationMessages(): Vector[RuntimeMessage] =
    validators.iterator.flatMap(_.validateMessage(valueProperty.get)).toVector

  private def resolve(message: RuntimeMessage): String =
    I18nRuntime
      .current(using self)
      .map(_.resolveNow(message))
      .getOrElse(fallbackResolver.resolve(message, I18nLocale.En))

  private def renderErrors(): Unit = {
    if (!observingLocale) I18nRuntime.current(using self).foreach { runtime =>
      observingLocale = true
      self.addDisposable(runtime.locale.observeWithoutInitial(_ => renderErrors()))
    }
    renderingErrors = true
    try errors.setAll((externalMessages ++ validatorMessages).map(resolve))
    finally renderingErrors = false
  }

  def validate(forceVisible: Boolean = false): Seq[String] = {
    val validationErrors = if (editableProperty.get) validationMessages() else Vector.empty

    if (forceVisible || dirtyProperty.get) {
      if (forceVisible) dirtyProperty.set(true)
      validatorMessages = validationErrors
    } else {
      // Not touched yet: validator messages stay out of sight, external ones stay where they are.
      validatorMessages = Vector.empty
    }
    renderErrors()

    validationErrors.map(resolve)
  }
}
