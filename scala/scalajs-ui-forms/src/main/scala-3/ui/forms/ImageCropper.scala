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

final class ImageCropper private (
    val name: String,
    val standalone: Boolean,
    configure: ImageCropper ?=> Cursor ?=> Unit
) extends AbstractComponent,
      Control[Media],
      Placeholder {

  import ImageCropper.*

  override val tagName: String = "div"

  override val valueProperty: Property[Media]       = Property(null)
  val committedValueProperty: Property[Media]       = Property(null)
  val sourceProperty: Property[Media]               = Property(null)
  val fileProperty: Property[File]                  = Property(null)
  val imageValidators: ListProperty[ImageValidator] = ListProperty()

  private val placeholderProperty   = Property("")
  private val previewSourceProperty = Property("")
  private val windowTitleProperty   = Property("Crop image")

  private var aspectRatioValue: Option[Double]  = None
  private var previewMaxWidthValue: Int         = 480
  private var previewMaxHeightValue: Int        = 360
  private var outputTypeValue: String           = "image/png"
  private var outputQualityValue: Double        = 0.92
  private var outputMaxWidthValue: Option[Int]  = None
  private var outputMaxHeightValue: Option[Int] = None
  private var thumbnailMaxWidthValue: Int       = 160
  private var thumbnailMaxHeightValue: Int      = 160

  private var fileInput: ImageCropperFileInput          = null
  private var previewBinding: Disposable                = Disposable.empty
  private var activeSession: ImageCropperDialog.Session = null

  override def compose(cursor: Cursor): Unit = {
    configure(using this)(using cursor)

    render(this, cursor) {
      addClass("image-cropper-field")
      addClass("image-cropper")
      setAttribute("name", name)
      setAttribute("tabindex", "0")
      setAttribute("role", "group")

      addDisposable(editableProperty.observe { editable =>
        setAttribute("aria-disabled", (!editable).toString)
        Option(fileInput).foreach(_.setDisabled(!editable))
      })

      on("focus")(_ => focusedProperty.set(true))
      on("blur") { _ =>
        focusedProperty.set(false)
        validate()
      }

      hbox {
        classes = Seq("toolbar")
        style {
          gap = "10px"
          alignItems = "center"
        }

        fileInput = ImageCropperFileInput.fileInput {
          on("change")(_ => onFileChange())
        }
        fileInput.setDisabled(!editableProperty.get)

        button(valueProperty.map(value => if (value == null) "Choose image" else "Replace image")) {
          buttonType("button")
          style {
            display = editableProperty.map(if (_) "" else "none")
          }
          onClick { _ =>
            if (editableProperty.get) Option(fileInput).foreach(_.click())
          }
        }

        button("Crop") {
          buttonType("button")
          style {
            display = editableProperty.flatMap { editable =>
              sourceProperty.map(source => if (editable && hasImageData(source)) "" else "none")
            }
          }
          onClick { _ =>
            if (editableProperty.get) currentSource().foreach(source => openCropWindow(source))
          }
        }

        button("Clear") {
          buttonType("button")
          style {
            display = editableProperty.flatMap { editable =>
              valueProperty.map(value => if (editable && value != null) "" else "none")
            }
          }
          onClick { _ =>
            if (editableProperty.get) {
              dirtyProperty.set(true)
              clear()
            }
          }
        }
      }

      div {
        classes = Seq("image-cropper__preview")
        style {
          flex = "1 1 auto"
          width = "100%"
          minWidth = "0"
          minHeight = "0"
          display = "flex"
          alignItems = "center"
          justifyContent = "center"
          position = "relative"
          overflow = "hidden"
          border = "1px solid var(--aj-surface-muted)"
          borderRadius = "6px"
          background = "var(--aj-canvas)"
        }

        image {
          classes = Seq("preview")
          src = previewSourceProperty
          alt = placeholderProperty
          style {
            display = previewSourceProperty.map(source => if (source.nonEmpty) "" else "none")
            width = "100%"
            height = "100%"
            minWidth = "0"
            minHeight = "0"
            border = "0"
            borderRadius = "0"
            objectFit = "cover"
          }
        }

        div {
          classes = Seq("image-cropper__placeholder")
          style {
            display = previewSourceProperty.map(source => if (source.isEmpty) "flex" else "none")
            width = "100%"
            height = "100%"
            alignItems = "center"
            justifyContent = "center"
            textAlign = "center"
            padding = "16px"
            color = "var(--aj-ink-muted)"
          }
          text(placeholderProperty.map(placeholderText)) {}
        }
      }

      addDisposable(valueProperty.observe { value =>
        sourceProperty.set(value)
        bindPreview(value)
        validate()
      })
      addDisposable(validators.observe(_ => validate()))
      addDisposable(imageValidators.observe(_ => validate()))
      addDisposable(dirtyProperty.observe(_ => validate()))
      addDisposable(Disposable {
        previewBinding.dispose()
        Option(activeSession).foreach { session =>
          session.closed = true
          activeSession = null
          closeCropWindow(session)
        }
      })

      if (!standalone) {
        val controller = FormContext.inject.getOrElse(
          throw new IllegalStateException(
            s"ImageCropper '$name' requires a Form or FieldSet context."
          )
        )
        controller.register(this)
        addDisposable(() => controller.unregister(this))
      }
    }
  }

  override protected def setPlaceholder(value: String): Unit =
    placeholderProperty.set(Option(value).getOrElse(""))

  override def validate(forceVisible: Boolean = false): Seq[String] = {
    val validationErrors =
      if (!editableProperty.get) Seq.empty
      else {
        val mediaErrors = validators.iterator.flatMap(_.validate(valueProperty.get)).toSeq
        val data        = Option(valueProperty.get)
          .flatMap(media => Option(media.data.get))
          .getOrElse("")
        mediaErrors ++ imageValidators.iterator.filterNot(_.validate(data)).map(_.message)
      }

    if (forceVisible || dirtyProperty.get) {
      if (forceVisible) dirtyProperty.set(true)
      errors.setAll(validationErrors)
    } else errors.clear()

    validationErrors
  }

  def openEditor(): Option[Viewport.WindowConf] =
    if (!editableProperty.get) None
    else currentSource().map(source => openCropWindow(source))

  def clear(): Unit = {
    Option(activeSession).foreach(cancelCropSession)
    Option(fileInput).foreach(_.value_=(""))
    fileProperty.set(null)
    sourceProperty.set(null)
    valueProperty.set(null)
    committedValueProperty.setAlways(null)
  }

  private def onFileChange(): Unit = {
    if (!editableProperty.get || fileInput == null) return

    val selectedFile = fileInput.files.flatMap(files => Option(files.item(0))).orNull
    fileProperty.set(selectedFile)

    if (selectedFile != null) {
      val reader                                     = new FileReader()
      val loadListener: js.Function1[dom.Event, Any] = _ =>
        if (editableProperty.get) {
          Option(reader.result)
            .map(_.toString.trim)
            .filter(_.nonEmpty)
            .foreach { encoded =>
              val previousValue = valueProperty.get
              val previousDirty = dirtyProperty.get
              val media         = mediaFromFile(selectedFile, encoded)
              dirtyProperty.set(true)
              sourceProperty.set(media)
              valueProperty.set(media)
              openCropWindow(media, previousValue, previousDirty)
            }
        }

      reader.addEventListener("load", loadListener)
      addDisposable(Disposable {
        reader.removeEventListener("load", loadListener)
        if (reader.readyState == FileReader.LOADING) reader.abort()
      })
      reader.readAsDataURL(selectedFile)
    }
  }

  private def currentSource(): Option[Media] =
    Option(sourceProperty.get)
      .orElse(Option(valueProperty.get))
      .filter(hasImageData)

  private def openCropWindow(
      source: Media,
      initialValue: Media = valueProperty.get,
      initialDirty: Boolean = dirtyProperty.get
  ): Viewport.WindowConf = {
    Option(activeSession).filterNot(_.closed) match {
      case Some(current) =>
        Viewport.touchWindow(current.windowConf)
        current.windowConf
      case None =>
        val session = ImageCropperDialog.Session(
          initialValue = initialValue,
          initialDirty = initialDirty
        )
        val width  = max(520, positive(previewMaxWidthValue, 480) + 40)
        val height = max(460, positive(previewMaxHeightValue, 360) + 100)
        val conf   = new Viewport.WindowConf(
          body = {
            ImageCropperDialog.dialog(this, source, session)
          },
          widthPx = width,
          heightPx = height,
          onClose = Some(_ => cancelCropSession(session))
        )
        conf.title = windowTitleProperty.get
        session.windowConf = conf
        activeSession = session
        Viewport.addWindow(conf)(using this)
    }
  }

  private[forms] def applyCropSession(
      session: ImageCropperDialog.Session,
      media: Media
  ): Unit =
    if (media != null && !session.closed) {
      session.applied = true
      session.closed = true
      activeSession = null
      dirtyProperty.set(true)
      valueProperty.set(media)
      committedValueProperty.set(media)
      closeCropWindow(session)
    }

  private[forms] def cancelCropSession(session: ImageCropperDialog.Session): Unit =
    if (!session.closed) {
      session.closed = true
      if (activeSession eq session) activeSession = null
      if (!session.applied) {
        Option(fileInput).foreach(_.value_=(""))
        fileProperty.set(null)
        valueProperty.set(session.initialValue)
        dirtyProperty.set(session.initialDirty)
      }
    }

  private[forms] def closeCropWindow(session: ImageCropperDialog.Session): Unit =
    Option(session.windowConf).foreach(Viewport.closeWindow)

  private def mediaFromFile(file: File, dataUrl: String): Media = {
    val fileName    = Option(file.name).getOrElse("")
    val contentType = Option(file.`type`)
      .map(_.trim)
      .filter(_.nonEmpty)
      .orElse(mimeTypeFromDataUrl(dataUrl))
      .getOrElse(normalizedOutputType)
    val data = base64FromDataUrl(dataUrl).getOrElse(dataUrl)

    new Media(
      name = Property(fileName),
      contentType = Property(contentType),
      data = Property(data),
      thumbnail = Property(
        new Thumbnail(
          name = Property(fileName),
          contentType = Property(contentType),
          data = Property("")
        )
      )
    )
  }

  private def bindPreview(media: Media): Unit = {
    previewBinding.dispose()
    val binding = new CompositeDisposable()
    previewBinding = binding
    var thumbnailBinding: Disposable = Disposable.empty
    binding.add(Disposable(thumbnailBinding.dispose()))

    def update(): Unit = previewSourceProperty.set(previewSource(media).getOrElse(""))
    def bindThumbnail(thumbnail: Thumbnail): Unit = {
      thumbnailBinding.dispose()
      val nested = new CompositeDisposable()
      thumbnailBinding = nested
      if (thumbnail != null) {
        nested.add(thumbnail.contentType.observe(_ => update()))
        nested.add(thumbnail.data.observe(_ => update()))
      }
    }

    if (media == null) update()
    else {
      binding.add(media.contentType.observe(_ => update()))
      binding.add(media.data.observe(_ => update()))
      binding.add(media.thumbnail.observe { thumbnail =>
        bindThumbnail(thumbnail)
        update()
      })
    }
  }

  private def previewSource(media: Media): Option[String] =
    Option(media).flatMap { value =>
      Option(value.thumbnail.get)
        .flatMap { thumbnail =>
          toDataUrl(thumbnail.contentType.get, thumbnail.data.get)
        }
        .orElse(toDataUrl(value.contentType.get, value.data.get))
    }

  private def placeholderText(value: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("No image selected")

  private[forms] def normalizedAspectRatio: Option[Double] =
    aspectRatioValue.filter(value => value > 0.0 && value.isFinite)

  private[forms] def previewMaxWidth: Int         = positive(previewMaxWidthValue, 480)
  private[forms] def previewMaxHeight: Int        = positive(previewMaxHeightValue, 360)
  private[forms] def outputMaxWidth: Option[Int]  = outputMaxWidthValue.filter(_ > 0)
  private[forms] def outputMaxHeight: Option[Int] = outputMaxHeightValue.filter(_ > 0)
  private[forms] def thumbnailMaxWidth: Int       = positive(thumbnailMaxWidthValue, 160)
  private[forms] def thumbnailMaxHeight: Int      = positive(thumbnailMaxHeightValue, 160)
  private[forms] def normalizedOutputType: String =
    Option(outputTypeValue).map(_.trim).filter(_.nonEmpty).getOrElse("image/png")
  private[forms] def normalizedOutputQuality: Double =
    outputQualityValue.max(0.0).min(1.0)

  private def bindWindowTitle(value: ReadOnlyProperty[String]): Unit = {
    windowTitleProperty.set(Option(value.get).getOrElse(""))
    addDisposable(value.observe(next => windowTitleProperty.set(Option(next).getOrElse(""))))
  }
}

object ImageCropper {
  export Editable.{editable, editable_=, editableProperty}
  export Placeholder.{placeholder, placeholder_=}

  trait ImageValidator {
    def validate(value: String): Boolean
    def message: String
  }

  object ImageValidator {
    def apply(errorMessage: String)(predicate: String => Boolean): ImageValidator =
      new ImageValidator {
        override def validate(value: String): Boolean = predicate(value)
        override def message: String                  = errorMessage
      }
  }

  def imageCropper(
      name: String,
      standalone: Boolean = false
  )(body: ImageCropper ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): ImageCropper =
    DslLayer.child(new ImageCropper(name, standalone, body)) {}

  def value(using cropper: ImageCropper): Media                   = cropper.valueProperty.get
  def value_=(media: Media)(using cropper: ImageCropper): Unit    = cropper.valueProperty.set(media)
  def valueProperty(using cropper: ImageCropper): Property[Media] = cropper.valueProperty
  def sourceProperty(using cropper: ImageCropper): Property[Media]      = cropper.sourceProperty
  def fileProperty(using cropper: ImageCropper): Property[File]         = cropper.fileProperty
  def errorsProperty(using cropper: ImageCropper): ListProperty[String] = cropper.errors

  def disabled(using cropper: ImageCropper): Boolean                = !cropper.editableProperty.get
  def disabled_=(value: Boolean)(using cropper: ImageCropper): Unit =
    cropper.editableProperty.set(!value)

  def aspectRatio(using cropper: ImageCropper): Option[Double]        = cropper.aspectRatioValue
  def aspectRatio_=(value: Double)(using cropper: ImageCropper): Unit =
    cropper.aspectRatioValue = Some(value)
  def aspectRatio_=(value: Option[Double])(using cropper: ImageCropper): Unit =
    cropper.aspectRatioValue = value

  def previewMaxWidth(using cropper: ImageCropper): Int = cropper.previewMaxWidthValue
  def previewMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.previewMaxWidthValue = value
  def previewMaxHeight(using cropper: ImageCropper): Int = cropper.previewMaxHeightValue
  def previewMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.previewMaxHeightValue = value

  def outputType(using cropper: ImageCropper): String                = cropper.outputTypeValue
  def outputType_=(value: String)(using cropper: ImageCropper): Unit =
    cropper.outputTypeValue = value
  def outputQuality(using cropper: ImageCropper): Double                = cropper.outputQualityValue
  def outputQuality_=(value: Double)(using cropper: ImageCropper): Unit =
    cropper.outputQualityValue = value

  def outputMaxWidth(using cropper: ImageCropper): Option[Int]        = cropper.outputMaxWidthValue
  def outputMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.outputMaxWidthValue = Some(value)
  def outputMaxWidth_=(value: Option[Int])(using cropper: ImageCropper): Unit =
    cropper.outputMaxWidthValue = value
  def outputMaxHeight(using cropper: ImageCropper): Option[Int] = cropper.outputMaxHeightValue
  def outputMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.outputMaxHeightValue = Some(value)
  def outputMaxHeight_=(value: Option[Int])(using cropper: ImageCropper): Unit =
    cropper.outputMaxHeightValue = value

  def thumbnailMaxWidth(using cropper: ImageCropper): Int = cropper.thumbnailMaxWidthValue
  def thumbnailMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.thumbnailMaxWidthValue = value
  def thumbnailMaxHeight(using cropper: ImageCropper): Int = cropper.thumbnailMaxHeightValue
  def thumbnailMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.thumbnailMaxHeightValue = value

  def windowTitle_=[T](value: T)(using
      cropper: ImageCropper,
      textValue: TextValue[T]
  ): Unit =
    cropper.bindWindowTitle(textValue.asReadOnlyProperty(value)(using cropper))

  def windowTitle(using cropper: ImageCropper): String =
    cropper.windowTitleProperty.get

  def addValidator(validator: ImageValidator)(using cropper: ImageCropper): Unit =
    cropper.imageValidators += validator

  private[forms] def hasImageData(media: Media): Boolean =
    Option(media).exists(value => Option(value.data.get).exists(_.trim.nonEmpty))

  private[forms] def mimeTypeFromDataUrl(dataUrl: String): Option[String] = {
    val value = Option(dataUrl).getOrElse("")
    if (!value.startsWith("data:")) None
    else {
      val semi  = value.indexOf(';', 5)
      val comma = value.indexOf(',', 5)
      Seq(semi, comma).filter(_ > 5).sorted.headOption.map(value.substring(5, _))
    }
  }

  private[forms] def base64FromDataUrl(dataUrl: String): Option[String] = {
    val value = Option(dataUrl).getOrElse("")
    if (!value.startsWith("data:")) None
    else {
      val comma = value.indexOf(',', 5)
      Option.when(comma >= 0)(value.substring(comma + 1))
    }
  }

  private[forms] def toDataUrl(contentType: String, dataOrUrl: String): Option[String] = {
    val data = Option(dataOrUrl).map(_.trim).getOrElse("")
    if (data.isEmpty) None
    else if (
      data.startsWith("data:") || data.startsWith("http://") ||
      data.startsWith("https://") || data.startsWith("blob:")
    ) Some(data)
    else {
      val normalizedType = Option(contentType).map(_.trim).getOrElse("")
      Option.when(normalizedType.nonEmpty)(s"data:$normalizedType;base64,$data")
    }
  }

  private[forms] def scaledSize(
      width: Int,
      height: Int,
      maxWidth: Option[Int],
      maxHeight: Option[Int]
  ): (Int, Int) = {
    val sourceWidth  = max(1, width)
    val sourceHeight = max(1, height)
    val widthScale   = maxWidth.filter(_ > 0).map(_.toDouble / sourceWidth).getOrElse(1.0)
    val heightScale  = maxHeight.filter(_ > 0).map(_.toDouble / sourceHeight).getOrElse(1.0)
    val scale        = min(1.0, min(widthScale, heightScale))
    max(1, math.round(sourceWidth * scale).toInt) ->
      max(1, math.round(sourceHeight * scale).toInt)
  }

  private[forms] def context2d(canvas: HTMLCanvasElement): Option[CanvasRenderingContext2D] =
    Option(canvas)
      .flatMap(value => Option(value.getContext("2d")))
      .map(_.asInstanceOf[CanvasRenderingContext2D])

  private[forms] def themeColor(name: String, fallback: String): String =
    try {
      Option(dom.window.getComputedStyle(dom.document.documentElement).getPropertyValue(name))
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse(fallback)
    } catch {
      case NonFatal(_) => fallback
    }

  private def positive(value: Int, fallback: Int): Int =
    if (value > 0) value else fallback
}
