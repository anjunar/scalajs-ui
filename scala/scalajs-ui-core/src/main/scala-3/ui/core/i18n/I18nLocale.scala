package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher

final case class I18nLocale(code: String) {
  require(code.nonEmpty, "Locale code must not be empty")

  def parent: Option[I18nLocale] =
    code.lastIndexOf('-') match {
      case index if index > 0 => Some(I18nLocale(code.substring(0, index)))
      case _                  => None
    }
}

object I18nLocale {
  val En: I18nLocale = I18nLocale("en")
}
