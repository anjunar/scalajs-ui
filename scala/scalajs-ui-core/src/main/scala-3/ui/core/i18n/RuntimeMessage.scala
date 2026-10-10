package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher

final case class RuntimeMessage(key: MessageKey, args: Vector[MessageArg]) {
  require(
    args.map(_.name) == key.placeholders,
    s"Runtime args ${args.map(_.name).mkString(", ")} do not match placeholders ${key.placeholders.mkString(", ")}"
  )
}

object RuntimeMessage {
  given runtimeMessageTextValue: TextValue[RuntimeMessage] with
    override def asReadOnlyProperty(value: RuntimeMessage)(using
        component: AbstractComponent
    ): ReadOnlyProperty[String] =
      I18nRuntime.require.text(value)
}
