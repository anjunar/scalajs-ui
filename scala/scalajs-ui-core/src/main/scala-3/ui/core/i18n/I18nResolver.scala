package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher

final class I18nResolver(catalog: MessageCatalog) {
  def resolve(message: RuntimeMessage, locale: I18nLocale): String =
    resolve(message, LocaleFallback(locale))

  def resolve(message: RuntimeMessage, fallback: LocaleFallback): String = {
    val pattern =
      catalog
        .entryFor(message.key)
        .flatMap(entry => fallback.chain.iterator.flatMap(entry.value.at).nextOption())
        .map(_.value)
        .getOrElse(message.key.source)

    interpolate(pattern, message.args)
  }

  def resolve(
      message: RuntimeMessage,
      locale: ReadOnlyProperty[I18nLocale]
  ): ReadOnlyProperty[String] =
    locale.map(resolve(message, _))

  /** Runs for every translated text; most have no placeholder and are returned as they are. */
  private def interpolate(pattern: String, args: Vector[MessageArg]): String =
    if (args.isEmpty || pattern.indexOf('{') < 0) pattern
    else {
      val values = args.iterator.map(arg => arg.name -> String.valueOf(arg.value)).toMap

      I18n.PlaceholderPattern.replaceAllIn(
        pattern,
        matched =>
          values
            .get(matched.group(1))
            .map(Matcher.quoteReplacement)
            .getOrElse(matched.matched)
      )
    }
}

