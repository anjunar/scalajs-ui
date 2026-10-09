package ui.forms

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.{addClass, classIf, classes}
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Div.div
import ui.core.layout.HBox.hbox
import ui.core.layout.Image.{alt, image, src}
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.{CompositeDisposable, Disposable, ListProperty, Property, ReadOnlyProperty}
import ui.core.text.TextValue
import ui.forms.Form.FormContext
import ui.viewport.Viewport
import org.scalajs.dom
import org.scalajs.dom.{
  CanvasRenderingContext2D,
  File,
  FileReader,
  HTMLCanvasElement,
  HTMLImageElement,
  HTMLInputElement,
  PointerEvent
}

import scala.math.{abs, max, min}
import scala.scalajs.js
import scala.util.control.NonFatal

private final class ImageCropperFileInput extends AbstractComponent {
  override val tagName: String = "input"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      setAttribute("type", "file")
      setAttribute("accept", "image/*")
      setAttribute("aria-hidden", "true")
      setAttribute("tabindex", "-1")
      style {
        display = "none"
      }
    }

  def click(): Unit                     = element.foreach(_.click())
  def files: Option[dom.FileList]       = element.flatMap(input => Option(input.files))
  def value_=(next: String): Unit       = element.foreach(_.value = next)
  def setDisabled(value: Boolean): Unit = setProperty("disabled", value)

  private def element: Option[HTMLInputElement] =
    if (!isBound) None
    else
      host match {
        case domHost: DomHostElement =>
          domHost.node match {
            case input: HTMLInputElement => Some(input)
            case _                       => None
          }
        case _ => None
      }
}

private object ImageCropperFileInput {
  def fileInput(body: ImageCropperFileInput ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor
  ): ImageCropperFileInput =
    DslLayer.child(new ImageCropperFileInput()) {
      body
    }
}

