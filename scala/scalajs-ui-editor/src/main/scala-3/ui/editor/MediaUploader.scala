package ui.editor

import org.scalajs.dom
import scala.concurrent.Future

trait MediaUploader {
  def upload(file: dom.File, signal: dom.AbortSignal): Future[UploadedMediaReference]
}
