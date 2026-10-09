package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher

enum MessageState {
  case Current
  case NeedsReview(reason: String)
  case Obsolete
}

