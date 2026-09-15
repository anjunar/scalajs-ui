package ui.editor

/** A document reference, independent of editor state, forms and storage. */
final case class ImageReference(
    src: String,
    alt: String = "",
    title: Option[String] = None,
    widthPx: Option[Int] = None,
    mediaId: Option[String] = None
) {
  def validated: ImageReference = {
    require(InternalImageUrl.valid(src), "Images require a permanent internal URL")
    require(alt != null, "Image alt text must not be null")
    require(widthPx.forall(_ > 0), "Image width must be a positive pixel integer")
    require(mediaId.forall(_.trim.nonEmpty), "Media ID must not be empty")
    this
  }
}

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
      val decoded = java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
      safe(decoded) && !decoded.contains("%") &&
      !"(?i)%2f|%5c|%3f|%23".r.findFirstIn(value).isDefined
    } catch { case _: IllegalArgumentException => false }
  }
}
