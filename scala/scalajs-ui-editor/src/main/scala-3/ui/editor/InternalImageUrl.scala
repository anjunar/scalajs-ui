package ui.editor
import java.net.URLDecoder

object InternalImageUrl {
  def valid(value: String): Boolean = {
    if (value == null || !value.startsWith("/") || value.startsWith("//")) return false
    // Decode only for validation: encoded separators, traversal and double encoding must not
    // acquire a different meaning in a proxy or backend than they have in the browser.
    def safe(text: String): Boolean =
      !text.exists(c => c.isControl || c.isWhitespace || c == '\\') &&
        !text.startsWith("//") && !text.contains("//") &&
        !text.takeWhile(c => c != '?' && c != '#').split('/').exists(p => p == "." || p == "..")
    if (!safe(value)) return false
    try {
      val decoded = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
      safe(decoded) && !decoded.contains("%") &&
      !"(?i)%2f|%5c|%3f|%23".r.findFirstIn(value).isDefined
    } catch { case _: IllegalArgumentException => false }
  }
}
