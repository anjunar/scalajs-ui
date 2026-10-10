package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.{ListProperty, Property}
import ui.forms.ArrayForm.*
import ui.forms.Form.form
import ui.forms.Input.input
import ui.forms.InputContainer.inputContainer
import ui.forms.SubForm.subForm
import ui.forms.validators.{NotBlank, NotBlankValidator}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.annotation.meta.field

private final class Person(
    @(NotBlank @field)("Name is required")
    var name: Property[String] = Property(""),
    var address: Property[Address] = Property(Address()),
    var tags: ListProperty[String] = ListProperty()
)

private object Person {
  def apply(): Person = new Person()
}
