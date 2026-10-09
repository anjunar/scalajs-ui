package ui.forms

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.forms.validators.NotBlankValidator

private final class ControlModel(
    var name: Property[String],
    var notes: Property[String],
    var kind: Property[String],
    var published: Property[Boolean]
)
