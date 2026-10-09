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

private final class ImageCropperCanvas extends AbstractComponent {
  override val tagName: String = "canvas"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      addClass("canvas")
      setAttribute("width", "1")
      setAttribute("height", "1")
    }

  def element: Option[HTMLCanvasElement] =
    host match {
      case domHost: DomHostElement =>
        domHost.node match {
          case canvas: HTMLCanvasElement => Some(canvas)
          case _                         => None
        }
      case _ => None
    }
}

private object ImageCropperCanvas {
  def canvas()(using AbstractComponent, Cursor): ImageCropperCanvas =
    DslLayer.child(new ImageCropperCanvas()) {}
}

