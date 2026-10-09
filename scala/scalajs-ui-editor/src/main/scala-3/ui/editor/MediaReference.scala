package ui.editor

import org.scalajs.dom
import scala.concurrent.Future

final case class MediaReference(src: String, mediaId: Option[String] = None)
