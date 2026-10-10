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
