package ui.forms

import ui.core.render.TextAreaContent

private final class TestTextAreaContent(var value: String, var defaultValue: String)
    extends TextAreaContent {
  override def setValue(next: String): Unit        = value = next
  override def setDefaultValue(next: String): Unit = defaultValue = next
  override def reset(): Unit                       = value = defaultValue
}
