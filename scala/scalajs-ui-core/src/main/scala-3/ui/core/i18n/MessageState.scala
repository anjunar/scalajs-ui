package ui.core.i18n

enum MessageState {
  case Current
  case NeedsReview(reason: String)
  case Obsolete
}
