package ui.editor

import org.scalajs.dom
import scala.concurrent.Future

final case class MediaUploadStatus(pending: Int = 0, error: Option[String] = None)
