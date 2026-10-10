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
import org.scalajs.dom.{CanvasRenderingContext2D, File, FileReader, HTMLCanvasElement, HTMLImageElement, HTMLInputElement, PointerEvent}

import scala.math.{abs, max, min}
import scala.scalajs.js
import scala.util.control.NonFatal

private final class ImageCropperDialog(
    field: ImageCropper,
    source: Media,
    session: ImageCropperDialog.Session
) extends AbstractComponent {

  import ImageCropperDialog.*

  override val tagName: String = "div"

  private var mainCanvas: HTMLCanvasElement       = null
  private var canvasComponent: ImageCropperCanvas = null
  private var loadedImage: HTMLImageElement       = null
  private var previewScale                        = 1.0
  private var crop: CropRect                      = null
  private var drag: DragState                     = null
  private var activePointerId: Double | Null      = null
  private var liveFrame: Option[Int]              = None
  private lazy val outputCanvas                   =
    dom.document.createElement("canvas").asInstanceOf[HTMLCanvasElement]
  private lazy val thumbnailCanvas =
    dom.document.createElement("canvas").asInstanceOf[HTMLCanvasElement]

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      addClass("image-cropper")
      addClass("image-cropper-dialog")

      hbox {
        classes = Seq("toolbar")
        style {
          gap = "10px"
          padding = "10px"
        }

        button("Apply") {
          buttonType("button")
          onClick(_ => cropToMedia().foreach(field.applyCropSession(session, _)))
        }
        button("Reset") {
          buttonType("button")
          onClick { _ =>
            if (loadedImage != null && mainCanvas != null) {
              crop = defaultCrop()
              renderCanvas()
              scheduleLivePreview()
            }
          }
        }
        button("Close") {
          buttonType("button")
          onClick { _ =>
            field.cancelCropSession(session)
            field.closeCropWindow(session)
          }
        }
      }

      div {
        classes = Seq("canvas-wrap")
        style {
          padding = "10px"
          display = "flex"
          justifyContent = "center"
          background = "var(--aj-canvas)"
        }
        canvasComponent = ImageCropperCanvas.canvas()
        mainCanvas = canvasComponent.element.orNull
      }

      addDisposable(Disposable {
        liveFrame.foreach(dom.window.cancelAnimationFrame)
        liveFrame = None
      })
    }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser && mainCanvas != null) {
      wireCanvasDragging()
      loadSourceImage()
    }

  private def loadSourceImage(): Unit = {
    val image = dom.document.createElement("img").asInstanceOf[HTMLImageElement]
    val loadListener: js.Function1[dom.Event, Any] = _ =>
      if (!session.closed) {
        loadedImage = image
        setupCanvasFor(image)
        crop = defaultCrop()
        renderCanvas()
        scheduleLivePreview()
      }

    image.addEventListener("load", loadListener)
    addDisposable(Disposable {
      image.removeEventListener("load", loadListener)
      image.src = ""
    })
    image.src = sourceImageUrl
  }

  private def sourceImageUrl: String =
    ImageCropper.toDataUrl(source.contentType.get, source.data.get).getOrElse("")

  private def setupCanvasFor(image: HTMLImageElement): Unit = {
    val width  = max(1, image.naturalWidth)
    val height = max(1, image.naturalHeight)
    previewScale = min(
      1.0,
      min(field.previewMaxWidth.toDouble / width, field.previewMaxHeight.toDouble / height)
    )
    mainCanvas.width = max(1, math.round(width * previewScale).toInt)
    mainCanvas.height = max(1, math.round(height * previewScale).toInt)
  }

  private def defaultCrop(): CropRect = {
    val canvasWidth  = mainCanvas.width.toDouble
    val canvasHeight = mainCanvas.height.toDouble
    field.normalizedAspectRatio match {
      case Some(ratio) =>
        var width  = canvasWidth
        var height = width / ratio
        if (height > canvasHeight) {
          height = canvasHeight
          width = height * ratio
        }
        CropRect((canvasWidth - width) / 2.0, (canvasHeight - height) / 2.0, width, height)
      case None => CropRect(0, 0, canvasWidth, canvasHeight)
    }
  }

  private def renderCanvas(): Unit =
    ImageCropper.context2d(mainCanvas).foreach { context =>
      if (loadedImage != null) {
        val canvasWidth  = mainCanvas.width.toDouble
        val canvasHeight = mainCanvas.height.toDouble
        context.clearRect(0, 0, canvasWidth, canvasHeight)
        context.drawImage(loadedImage, 0, 0, canvasWidth, canvasHeight)

        Option(crop).map(_.normalize()).filter(rect => rect.width > 0 && rect.height > 0).foreach {
          rect =>
            context.fillStyle = ImageCropper.themeColor(
              "--aj-surface-backdrop",
              "rgba(0, 0, 0, 0.32)"
            )
            context.fillRect(0, 0, canvasWidth, canvasHeight)
            context.save()
            context.beginPath()
            context.rect(rect.x, rect.y, rect.width, rect.height)
            context.clip()
            context.drawImage(loadedImage, 0, 0, canvasWidth, canvasHeight)
            context.restore()
            context.strokeStyle = ImageCropper.themeColor(
              "--aj-ink-inverse",
              "rgba(255, 255, 255, 0.94)"
            )
            context.lineWidth = 1
            context.strokeRect(
              rect.x + 0.5,
              rect.y + 0.5,
              max(0.0, rect.width - 1.0),
              max(0.0, rect.height - 1.0)
            )

            val handleSize                                         = 6.0
            def drawHandle(centerX: Double, centerY: Double): Unit = {
              context.fillStyle = ImageCropper.themeColor(
                "--aj-ink-inverse",
                "rgba(255, 255, 255, 0.94)"
              )
              context.fillRect(
                centerX - handleSize / 2,
                centerY - handleSize / 2,
                handleSize,
                handleSize
              )
              context.strokeStyle = ImageCropper.themeColor(
                "--aj-surface-scrim",
                "rgba(0, 0, 0, 0.22)"
              )
              context.strokeRect(
                centerX - handleSize / 2 + 0.5,
                centerY - handleSize / 2 + 0.5,
                handleSize - 1,
                handleSize - 1
              )
            }
            drawHandle(rect.x, rect.y)
            drawHandle(rect.x + rect.width, rect.y)
            drawHandle(rect.x, rect.y + rect.height)
            drawHandle(rect.x + rect.width, rect.y + rect.height)
        }
      }
    }

  private def scheduleLivePreview(): Unit =
    if (!session.closed && liveFrame.isEmpty) {
      liveFrame = Some(dom.window.requestAnimationFrame { _ =>
        liveFrame = None
        if (!session.closed) cropToMedia().foreach(field.valueProperty.set)
      })
    }

  private def wireCanvasDragging(): Unit = {
    addDisposable(canvasComponent.onDisposable("pointerdown") { event =>
      event.raw match {
        case pointer: PointerEvent if loadedImage != null && pointer.button == 0 =>
          event.preventDefault()
          event.stopPropagation()
          activePointerId = pointer.pointerId
          try mainCanvas.setPointerCapture(pointer.pointerId)
          catch { case NonFatal(_) => () }
          val point   = canvasPoint(pointer)
          val current = Option(crop).map(_.normalize()).orNull
          val mode    = hitTest(current, point.x, point.y)
          drag = DragState(mode, point.x, point.y, current)
          if (mode == DragMode.New) crop = CropRect(point.x, point.y, 1.0, 1.0)
          renderCanvas()
        case _ => ()
      }
    })

    addDisposable(canvasComponent.onDisposable("lostpointercapture") { event =>
      event.raw match {
        case pointer: PointerEvent if activePointerId == pointer.pointerId => finishPointer(pointer)
        case _                                                             => ()
      }
    })

    val moveListener: js.Function1[PointerEvent, Any] = pointer =>
      if (drag != null && loadedImage != null && activePointerId == pointer.pointerId) {
        pointer.preventDefault()
        pointer.stopPropagation()
        updateCrop(pointer)
      }
    val upListener: js.Function1[PointerEvent, Any] = pointer =>
      if (activePointerId == pointer.pointerId) finishPointer(pointer)

    dom.window.addEventListener("pointermove", moveListener)
    dom.window.addEventListener("pointerup", upListener)
    dom.window.addEventListener("pointercancel", upListener)
    addDisposable(Disposable(dom.window.removeEventListener("pointermove", moveListener)))
    addDisposable(Disposable(dom.window.removeEventListener("pointerup", upListener)))
    addDisposable(Disposable(dom.window.removeEventListener("pointercancel", upListener)))
  }

  private def updateCrop(pointer: PointerEvent): Unit = {
    val state        = drag
    val point        = canvasPoint(pointer)
    val canvasWidth  = mainCanvas.width.toDouble
    val canvasHeight = mainCanvas.height.toDouble
    val minimumSize  = 8.0

    def clampMove(x: Double, y: Double, width: Double, height: Double): CropRect =
      CropRect(
        x.max(0.0).min(max(0.0, canvasWidth - width)),
        y.max(0.0).min(max(0.0, canvasHeight - height)),
        width,
        height
      )

    def clampRect(value: CropRect): CropRect = {
      val normalized = value.normalize()
      var x          = normalized.x
      var y          = normalized.y
      var width      = max(minimumSize, normalized.width).min(canvasWidth)
      var height     = max(minimumSize, normalized.height).min(canvasHeight)
      if (x < 0) x = 0
      if (y < 0) y = 0
      if (x + width > canvasWidth) x = canvasWidth - width
      if (y + height > canvasHeight) y = canvasHeight - height
      CropRect(x, y, width, height)
    }

    def withAspect(anchorX: Double, anchorY: Double, dx: Double, dy: Double): CropRect =
      field.normalizedAspectRatio match {
        case Some(ratio) =>
          val normalized      = CropRect(anchorX, anchorY, dx, dy).normalize()
          val signX           = if (dx >= 0) 1.0 else -1.0
          val signY           = if (dy >= 0) 1.0 else -1.0
          val (width, height) =
            if (normalized.height == 0 || normalized.width / normalized.height <= ratio)
              normalized.width               -> (normalized.width / ratio)
            else (normalized.height * ratio) -> normalized.height
          CropRect(anchorX, anchorY, width * signX, height * signY)
        case None => CropRect(anchorX, anchorY, dx, dy)
      }

    crop = state.mode match {
      case DragMode.Move =>
        clampMove(
          state.startRect.x + point.x - state.startX,
          state.startRect.y + point.y - state.startY,
          state.startRect.width,
          state.startRect.height
        )
      case DragMode.New =>
        clampRect(
          withAspect(state.startX, state.startY, point.x - state.startX, point.y - state.startY)
        )
      case DragMode.ResizeNW =>
        val anchorX = state.startRect.x + state.startRect.width
        val anchorY = state.startRect.y + state.startRect.height
        clampRect(withAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))
      case DragMode.ResizeNE =>
        val anchorX = state.startRect.x
        val anchorY = state.startRect.y + state.startRect.height
        clampRect(withAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))
      case DragMode.ResizeSW =>
        val anchorX = state.startRect.x + state.startRect.width
        val anchorY = state.startRect.y
        clampRect(withAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))
      case DragMode.ResizeSE =>
        clampRect(
          withAspect(
            state.startRect.x,
            state.startRect.y,
            point.x - state.startRect.x,
            point.y - state.startRect.y
          )
        )
    }
    renderCanvas()
    scheduleLivePreview()
  }

  private def finishPointer(pointer: PointerEvent): Unit = {
    pointer.preventDefault()
    pointer.stopPropagation()
    activePointerId = null
    drag = null
    try {
      if (mainCanvas.hasPointerCapture(pointer.pointerId))
        mainCanvas.releasePointerCapture(pointer.pointerId)
    } catch { case NonFatal(_) => () }
    renderCanvas()
  }

  private def canvasPoint(pointer: PointerEvent): Point = {
    val bounds = mainCanvas.getBoundingClientRect()
    val scaleX = if (bounds.width == 0) 1.0 else mainCanvas.width.toDouble / bounds.width
    val scaleY = if (bounds.height == 0) 1.0 else mainCanvas.height.toDouble / bounds.height
    Point(
      (pointer.clientX.toDouble - bounds.left) * scaleX,
      (pointer.clientY.toDouble - bounds.top) * scaleY
    )
  }

  private def hitTest(rect: CropRect, x: Double, y: Double): DragMode = {
    if (rect == null) return DragMode.New
    val normalized                               = rect.normalize()
    val tolerance                                = 10.0
    def near(left: Double, top: Double): Boolean =
      abs(x - left) <= tolerance && abs(y - top) <= tolerance

    if (near(normalized.x, normalized.y)) DragMode.ResizeNW
    else if (near(normalized.x + normalized.width, normalized.y)) DragMode.ResizeNE
    else if (near(normalized.x, normalized.y + normalized.height)) DragMode.ResizeSW
    else if (near(normalized.x + normalized.width, normalized.y + normalized.height))
      DragMode.ResizeSE
    else if (
      x >= normalized.x && x <= normalized.x + normalized.width &&
      y >= normalized.y && y <= normalized.y + normalized.height
    ) DragMode.Move
    else DragMode.New
  }

  private def cropToMedia(): Option[Media] = {
    if (loadedImage == null || mainCanvas == null) return None
    val selected = Option(crop).map(_.normalize()).getOrElse(defaultCrop().normalize())
    if (selected.width <= 0 || selected.height <= 0 || previewScale <= 0) return None

    val sourceX      = selected.x / previewScale
    val sourceY      = selected.y / previewScale
    val sourceWidth  = selected.width / previewScale
    val sourceHeight = selected.height / previewScale
    val rawWidth     = max(1, math.round(sourceWidth).toInt)
    val rawHeight    = max(1, math.round(sourceHeight).toInt)
    val outputSize   = ImageCropper.scaledSize(
      rawWidth,
      rawHeight,
      field.outputMaxWidth,
      field.outputMaxHeight
    )
    val thumbnailSize = ImageCropper.scaledSize(
      rawWidth,
      rawHeight,
      Some(field.thumbnailMaxWidth),
      Some(field.thumbnailMaxHeight)
    )

    for {
      outputData <- renderCroppedData(
        outputCanvas,
        sourceX,
        sourceY,
        sourceWidth,
        sourceHeight,
        outputSize._1,
        outputSize._2
      )
      thumbnailData <- renderCroppedData(
        thumbnailCanvas,
        sourceX,
        sourceY,
        sourceWidth,
        sourceHeight,
        thumbnailSize._1,
        thumbnailSize._2
      )
    } yield {
      val sourceName    = Option(source.name.get).getOrElse("")
      val thumbnailName = Option(source.thumbnail.get)
        .flatMap(thumbnail => Option(thumbnail.name.get).map(_.trim).filter(_.nonEmpty))
        .getOrElse(sourceName)
      val contentType = field.normalizedOutputType
      new Media(
        name = Property(sourceName),
        contentType = Property(contentType),
        data = Property(outputData),
        thumbnail = Property(
          new Thumbnail(
            name = Property(thumbnailName),
            contentType = Property(contentType),
            data = Property(thumbnailData)
          )
        )
      )
    }
  }

  private def renderCroppedData(
      canvas: HTMLCanvasElement,
      sourceX: Double,
      sourceY: Double,
      sourceWidth: Double,
      sourceHeight: Double,
      targetWidth: Int,
      targetHeight: Int
  ): Option[String] =
    try {
      canvas.width = targetWidth
      canvas.height = targetHeight
      ImageCropper.context2d(canvas).flatMap { context =>
        context.clearRect(0, 0, targetWidth, targetHeight)
        context.drawImage(
          loadedImage,
          sourceX,
          sourceY,
          sourceWidth,
          sourceHeight,
          0,
          0,
          targetWidth,
          targetHeight
        )
        ImageCropper.base64FromDataUrl(
          canvas.toDataURL(field.normalizedOutputType, field.normalizedOutputQuality)
        )
      }
    } catch {
      case NonFatal(_) => None
    }
}

private object ImageCropperDialog {
  final case class Point(x: Double, y: Double)
  final case class CropRect(x: Double, y: Double, width: Double, height: Double) {
    def normalize(): CropRect =
      CropRect(
        if (width >= 0) x else x + width,
        if (height >= 0) y else y + height,
        abs(width),
        abs(height)
      )
  }
  final case class DragState(
      mode: DragMode,
      startX: Double,
      startY: Double,
      startRect: CropRect
  )
  enum DragMode {
    case New, Move, ResizeNW, ResizeNE, ResizeSW, ResizeSE
  }
  final case class Session(
      initialValue: Media,
      initialDirty: Boolean,
      var applied: Boolean = false,
      var closed: Boolean = false,
      var windowConf: Viewport.WindowConf = null
  )

  def dialog(
      field: ImageCropper,
      source: Media,
      session: Session
  )(using AbstractComponent, Cursor): ImageCropperDialog =
    DslLayer.child(new ImageCropperDialog(field, source, session)) {}
}
