package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher
import java.lang.{Long as JavaLong}

object I18n {

  /** A plain runtime string, e.g. an annotation or server error, using the same key as the macro.
    * Use the i18n interpolator for messages with arguments.
    */
  def literal(source: String): RuntimeMessage = {
    val fingerprint = source.foldLeft(0xcbf29ce484222325L) { (hash, char) =>
      (hash ^ char.toLong) * 0x100000001b3L
    }
    RuntimeMessage(
      MessageKey(
        source,
        None,
        MessageFingerprint(JavaLong.toUnsignedString(fingerprint, 16)),
        Vector.empty,
        None
      ),
      Vector.empty
    )
  }

  // Compiled once: a Regex compiles its pattern when it is constructed.
  private[i18n] val PlaceholderPattern = "\\{([A-Za-z][A-Za-z0-9_]*)\\}".r
  private val PlaceholderNamePattern   = "[A-Za-z][A-Za-z0-9_]*".r

  def named(name: String, value: Any): NamedPlaceholder = {
    require(PlaceholderNamePattern.matches(name), s"Invalid placeholder name '$name'")
    NamedPlaceholder(name, value)
  }

  def context(value: String): MessageContext =
    MessageContext(value)

  def entry(key: MessageKey): CatalogEntryBuilder =
    CatalogEntryBuilder(key)
}
