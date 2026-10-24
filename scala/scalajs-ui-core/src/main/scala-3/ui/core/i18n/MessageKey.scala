package ui.core.i18n

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
