package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.forms.Form.form
import ui.forms.Input.input
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

private final class Account(
    var email: Property[String] = Property("")
)
