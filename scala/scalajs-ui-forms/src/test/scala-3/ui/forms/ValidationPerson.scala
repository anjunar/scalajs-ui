package ui.forms

import org.scalatest.funsuite.AnyFunSuite
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.i18n.{I18n, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog, i18n}
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.forms.Form.form
import ui.forms.Input.input
import ui.forms.InputContainer.inputContainer
import ui.forms.SubForm.subForm
import ui.forms.validators.*

import scala.annotation.meta.field

class ValidationPerson {
  @(NotBlank @field)(message = "Name is required")
  val name: Property[String]               = Property("")
  val contact: Property[ValidationContact] = Property(new ValidationContact)
}
