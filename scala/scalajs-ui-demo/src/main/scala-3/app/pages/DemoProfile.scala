package app.pages

import app.AppI18n
import app.components.Showcase
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.Button.button
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.Cursor
import ui.core.state.Property
import ui.forms.Form.form
import ui.forms.Input.input
import ui.forms.InputContainer.inputContainer
import ui.forms.validators.{EmailConstraint, NotBlank}
import ui.core.i18n.{I18nRuntime, i18n}

import scala.annotation.meta.field
import ui.forms.Input.inputType

private final class DemoProfile(
    @(NotBlank @field)("Name is required")
    var name: Property[String] = Property(""),
    @(NotBlank @field)("Email is required")
    @(EmailConstraint @field)()
    var email: Property[String] = Property("")
)

private object DemoProfile {
  def apply(): DemoProfile = new DemoProfile()
}
