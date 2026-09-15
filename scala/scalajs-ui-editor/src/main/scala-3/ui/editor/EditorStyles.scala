package ui.editor

/** CSS class contract shared by the semantic and enhanced editor presentations. */
private[editor] object EditorStyles {
  val paragraph: String      = "editor-paragraph"
  val quote: String          = "editor-quote"
  val horizontalRule: String = "editor-horizontal-rule"
  val unorderedList: String  = "editor-list-ul"
  val orderedList: String    = "editor-list-ol"
  val listItem: String       = "editor-listitem"
  val bold: String           = "editor-text-bold"
  val italic: String         = "editor-text-italic"
  val underline: String      = "editor-text-underline"
  val strikethrough: String  = "editor-text-strikethrough"
  val code: String           = "editor-text-code"

  def heading(level: Int): String = s"editor-heading-h$level"
}
