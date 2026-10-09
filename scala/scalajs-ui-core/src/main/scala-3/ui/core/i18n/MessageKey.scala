package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher

final case class MessageKey(
    source: String,
    context: Option[MessageContext],
    fingerprint: MessageFingerprint,
    placeholders: Vector[String],
    position: Option[MessageSourcePosition]
) {
  require(source.nonEmpty, "Message source must not be empty")
  require(placeholders.distinct == placeholders, s"Duplicate placeholders in message '$source'")
}

