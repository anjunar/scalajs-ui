package ui.editor

import ui.core.component.AbstractComponent
import ui.core.context.UrlScope

import ui.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.{addClass, classes}
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Condition.when
import ui.core.layout.Div
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.{Disposable, Property}
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.editor.plugins.EditorPlugin
import ui.forms.Form.FormContext
import ui.forms.{Control, Editable, Placeholder}

import org.scalajs.dom.{HTMLDivElement, HTMLElement}

import scala.collection.mutable
import scala.compiletime.uninitialized

enum EditorToolbarMode:
  case Ribbon, Menu, Floating

/** A Markdown-valued editor.
  *
  * Markdown is the public value in every environment. On the server, readonly mode produces
  * semantic HTML and editable mode produces a textarea. In the browser both server representations
  * are progressively enhanced to Ember after compose; its own editable state then decides whether
  * the mounted surface is interactive. Ember imports and exports Markdown at the component
  * boundary.
  */
final class Editor private[editor] (
    val name: String,
    val standalone: Boolean,
    configure: Editor ?=> Cursor ?=> Unit
) extends AbstractComponent,
      Control[String],
      Placeholder {

  override val tagName: String = "div"

  override val valueProperty: Property[String] = Property("")

  private val placeholderProperty                      = Property("")
  private val presentationError                        = Property("")
  private val sourceModeLabel                          = Property("Markdown")
  private var sourceMode                               = false
  private val plugins                                  = mutable.ArrayBuffer.empty[EditorPlugin]
  var mediaUploader: Option[MediaUploader]             = None
  var mediaUrlPolicy: MediaUrlPolicy                   = MediaUrlPolicy.internal
  var onMediaStatus: MediaUploadStatus => Unit         = _ => ()
  val mediaStatusProperty: Property[MediaUploadStatus] = Property(MediaUploadStatus())

  /** Called each time the visual surface mounts a new Ember session -- in the browser only, and
    * again after the source view hands back to the visual one. The session belongs to the surface;
    * see [[NativeEditorBinding]].
    */
  private[ui] var onNativeSession: Option[NativeEditorBinding => Unit] = None

  private var toolbarModeValue: EditorToolbarMode       = EditorToolbarMode.Ribbon
  private var editUrlValue: Option[String]              = None
  private var editLabelValue                            = "Edit"
  private var readonlyUrlValue: Option[String]          = None
  private var readonlyLabelValue                        = "Readonly"
  private var toolbarHost: Div                          = uninitialized
  private var fallbackHost: Div                         = uninitialized
  private var surfaceHost: Div                          = uninitialized
  private var nativeAdapter: NativeEditorAdapter | Null = null
  private var browserRendering                          = false
  private var configuredEditable: Option[Either[Boolean, Property[Boolean]]] = None

  override def compose(cursor: Cursor): Unit = {
    configure(using this)(using cursor)

    render(this, cursor) {
      // Form registration can synchronously replace the constructor value with the model value.
      // It must happen before the dynamic Markdown fallback is built, otherwise hydration would
      // claim a different subtree than SSR produced.
      installControlObservers()
      registerWithForm()
      valueProperty.set(MarkdownImages.discardEmbedded(valueProperty.get))
      validators += new ui.forms.validators.Validator[String] {
        def validate(value: String): Option[String] =
          MarkdownImages.validationError(value, mediaUrlPolicy)
      }
      installConfiguredEditable()
      initializeUrlMode()

      browserRendering = cursor.isBrowser
      addClass("scalajs-ui-editor-host")
      addClass("scalajs-ui-editor-host--markdown")
      setAttribute("name", name)
      setAttribute("role", "group")
      setAttribute("data-scalajs-ui-editor-format", "markdown")
      setAttribute("data-scalajs-ui-editor-loading", cursor.isBrowser.toString)

      div {
        classes = Seq("scalajs-ui-editor")

        div {
          classes = Seq("scalajs-ui-editor__shell")

          toolbarHost = div {
            classes = Seq("scalajs-ui-editor__toolbar")
            setDslAttribute("aria-label", "Editor toolbar")
            style { display = "none" }
          }

          div {
            classes = Seq("scalajs-ui-editor__media-status")
            setDslAttribute("role", "status")
            setDslAttribute("aria-live", "polite")
            style {
              display = mediaStatusProperty.map(status =>
                if (status.pending > 0 || status.error.nonEmpty) "" else "none"
              )
            }
            text(
              mediaStatusProperty.map(status =>
                status.error.getOrElse(
                  if (status.pending > 0) s"Uploading ${status.pending} image(s)…" else ""
                )
              )
            ) {}
          }

          div {
            classes = Seq("scalajs-ui-editor__markdown-actions")
            ui.core.layout.Button.button(sourceModeLabel) {
              ui.core.layout.Button.buttonType("button")
              ui.core.dsl.EventDsl.onClick { _ =>
                if (sourceMode) {
                  sourceMode = false
                  mountNative()
                } else {
                  sourceMode = true
                  destroyEditor()
                  syncPresentation(editableProperty.get)
                }
              }
            }
            dynamic(editableProperty.map[AbstractComponent] { editable =>
              if (editable)
                new MarkdownModeLink(
                  readonlyUrlValue.getOrElse(modeUrl(editable = false)),
                  readonlyLabelValue,
                  readonly = true,
                  onActivate = () => editableProperty.set(false)
                )
              else
                new MarkdownModeLink(
                  editUrlValue.getOrElse(modeUrl(editable = true)),
                  editLabelValue,
                  readonly = false,
                  onActivate = () => editableProperty.set(true)
                )
            })
          }

          div {
            setDslAttribute("role", "status")
            text(presentationError) {}
          }

          div {
            classes = Seq("scalajs-ui-editor__surface-wrap")

            fallbackHost = div {
              classes = Seq("scalajs-ui-editor__fallback")
              dynamic(editableProperty.map[AbstractComponent] { editable =>
                if (editable)
                  new MarkdownTextArea(
                    name,
                    valueProperty,
                    placeholderProperty,
                    publishMarkdown,
                    updateFocus
                  )
                else new MarkdownReadonly(valueProperty, mediaUrlPolicy)
              })
            }

            surfaceHost = div {
              classes = Seq(
                "scalajs-ui-editor__surface",
                "ember-editor-container",
                "ember-editor-input"
              )
              setDslAttribute("role", "textbox")
              setDslAttribute("aria-multiline", "true")
              setDslAttribute("contenteditable", editableProperty.get.toString)
              setDslAttribute("aria-readonly", (!editableProperty.get).toString)
              setDslAttribute("spellcheck", "true")
              style {
                display = "none"
                opacity = "0"
              }
            }

            div {
              classes = Seq("scalajs-ui-editor__placeholder")
              style {
                display = valueProperty.flatMap { value =>
                  placeholderProperty.map { placeholder =>
                    if (value.trim.isEmpty && placeholder.trim.nonEmpty) "" else "none"
                  }
                }
              }
              when(placeholderProperty.map(_.trim.nonEmpty)) {
                text(placeholderProperty.map(_.trim)) {}
              }
            }
          }
        }
      }
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) {
      domElement[HTMLElement](fallbackHost)
        .flatMap(host => Option(host.querySelector("textarea")))
        .collect { case input: org.scalajs.dom.HTMLTextAreaElement => input }
        .filter(input => input.value != input.textContent)
        .foreach(input => publishMarkdown(input.value))
      mountNative()
    }

  override protected def setPlaceholder(value: String): Unit =
    placeholderProperty.set(Option(value).getOrElse(""))

  private[editor] def registerPlugin(plugin: EditorPlugin): Unit =
    if (!plugins.exists(_.name == plugin.name)) plugins += plugin

  private[editor] def toolbarMode: EditorToolbarMode = toolbarModeValue

  private[editor] def toolbarMode_=(mode: EditorToolbarMode): Unit =
    toolbarModeValue = Option(mode).getOrElse(EditorToolbarMode.Ribbon)

  private[editor] def editUrl: Option[String] = editUrlValue

  private[editor] def editUrl_=(value: String): Unit =
    editUrlValue = Option(value).map(_.trim).filter(_.nonEmpty)

  private[editor] def editLabel: String = editLabelValue

  private[editor] def editLabel_=(value: String): Unit =
    editLabelValue = Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("Edit")

  private[editor] def readonlyUrl: Option[String] = readonlyUrlValue

  private[editor] def readonlyUrl_=(value: String): Unit =
    readonlyUrlValue = Option(value).map(_.trim).filter(_.nonEmpty)

  private[editor] def readonlyLabel: String = readonlyLabelValue

  private[editor] def readonlyLabel_=(value: String): Unit =
    readonlyLabelValue = Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("Readonly")

  private[ui] def configureEditable(value: Boolean): Unit = {
    configuredEditable = Some(Left(value))
    editableProperty.set(value)
  }

  private[ui] def configureEditable(value: Property[Boolean]): Unit = {
    configuredEditable = Some(Right(value))
    editableProperty.set(value.get)
  }

  private def installConfiguredEditable(): Unit =
    configuredEditable.foreach {
      case Left(value)  => editableProperty.set(value)
      case Right(value) =>
        addDisposable(Property.subscribeBidirectional(value, editableProperty))
    }

  private def initializeUrlMode(): Unit =
    UrlScope
      .current(using this)
      .flatMap(scope => EditorModeUrl.read(scope.url, name))
      .foreach(editableProperty.set)

  private def modeUrl(editable: Boolean): String =
    UrlScope
      .current(using this)
      .map(scope => EditorModeUrl.write(scope.url, name, editable))
      .getOrElse(EditorModeUrl.fallback(name, editable))

  private def installControlObservers(): Unit = {
    addDisposable(valueProperty.observe { _ => validate() })
    addDisposable(valueProperty.observeWithoutInitial(syncExternalValue))
    addDisposable(validators.observe(_ => validate()))
    addDisposable(dirtyProperty.observe(_ => validate()))
    addDisposable(editableProperty.observe { editable =>
      setAttribute("aria-disabled", (!editable).toString)
      updateEditable(editable)
    })
    addDisposable(Disposable(destroyEditor()))
  }

  private def registerWithForm(): Unit =
    if (!standalone) {
      val controller = FormContext
        .inject(using this)
        .getOrElse(
          throw new IllegalStateException(s"Editor '$name' requires a Form or FieldSet context.")
        )
      controller.register(this)
      addDisposable(() => controller.unregister(this))
    }

  private def mountNative(): Unit =
    if (nativeAdapter == null && !sourceMode)
      for {
        surface <- domElement[HTMLDivElement](surfaceHost)
        toolbar <- domElement[HTMLElement](toolbarHost)
      } {
        val adapter = new NativeEditorAdapter(
          name = name,
          owner = this,
          surface = surface,
          toolbar = toolbar,
          plugins = plugins.toSeq,
          toolbarMode = toolbarModeValue,
          mediaUploader = mediaUploader,
          mediaUrlPolicy = mediaUrlPolicy,
          onMediaStatus = status => { mediaStatusProperty.set(status); onMediaStatus(status) },
          onMarkdownChanged = publishMarkdown,
          onFocusChanged = updateFocus
        )
        try {
          adapter.mount(valueProperty.get, editableProperty.get)
          nativeAdapter = adapter
          presentationError.set("")
        } catch {
          case scala.util.control.NonFatal(error) =>
            adapter.close()
            sourceMode = true
            presentationError.set(
              "Markdown-Ansicht: " + Option(error.getMessage)
                .getOrElse("Darstellung nicht verfügbar.")
            )
        }
        syncPresentation(editableProperty.get)
        setAttribute("data-scalajs-ui-editor-loading", "false")
        // Outside the try: a failing listener is the application's error, not a reason to fall
        // back to the Markdown view.
        Option(nativeAdapter).foreach(mounted => onNativeSession.foreach(_(mounted.binding)))
      }

  private def updateEditable(editable: Boolean): Unit = {
    if (
      browserRendering && editable && nativeAdapter == null && Option(surfaceHost).exists(
        _.isBound
      )
    )
      mountNative()

    Option(nativeAdapter).foreach(_.setEditable(editable))
    syncPresentation(editable)
  }

  private def syncPresentation(editable: Boolean): Unit = {
    sourceModeLabel.set(if (sourceMode) "Visuell" else "Markdown")
    // Once Ember has been mounted in the browser, keep it as the live surface when the
    // control becomes readonly. The adapter applies Ember's readonly state and hides the
    // toolbar; falling back to MarkdownRenderer here would unnecessarily replace the DOM and
    // lose the hydrated Ember surface.
    val enhanced = browserRendering && nativeAdapter != null
    Option(fallbackHost).foreach(_.setStyle("display", if (enhanced) "none" else ""))
    Option(surfaceHost).foreach { surface =>
      surface.setStyle("display", if (enhanced) "" else "none")
      surface.setStyle("opacity", if (enhanced) "1" else "0")
    }
    if (!enhanced) Option(toolbarHost).foreach(_.setStyle("display", "none"))
  }

  private def publishMarkdown(markdown: String): Unit = {
    dirtyProperty.set(true)
    val value = MarkdownImages.discardEmbedded(markdown)
    if (valueProperty.get != value) valueProperty.set(value)
  }

  private def syncExternalValue(value: String): Unit =
    Option(nativeAdapter).foreach { adapter =>
      try adapter.syncMarkdown(value)
      catch {
        case scala.util.control.NonFatal(error) =>
          sourceMode = true
          destroyEditor()
          presentationError.set(
            "Markdown-Ansicht: " + Option(error.getMessage).getOrElse(
              "Darstellung nicht verfügbar."
            )
          )
          syncPresentation(editableProperty.get)
      }
    }

  private def updateFocus(focused: Boolean): Unit = {
    focusedProperty.set(focused)
    if (!focused) validate()
  }

  private def destroyEditor(): Unit = {
    Option(nativeAdapter).foreach(_.close())
    nativeAdapter = null
    focusedProperty.set(false)
  }

  private def domElement[A <: HTMLElement](component: AbstractComponent): Option[A] =
    Option(component).flatMap { current =>
      current.host match {
        case domHost: DomHostElement => Some(domHost.node.asInstanceOf[A])
        case _                       => None
      }
    }

}

object Editor {
  export Editable.{editable, editable_=, editableProperty}
  export Placeholder.{placeholder, placeholder_=}

  def editor(
      name: String,
      standalone: Boolean = false
  )(body: Editor ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Editor =
    DslLayer.child(new Editor(name, standalone, body)) {}

  def mediaUploader_=(uploader: MediaUploader)(using editor: Editor): Unit = editor.mediaUploader =
    Some(uploader)
  def mediaUrlPolicy_=(policy: MediaUrlPolicy)(using editor: Editor): Unit = editor.mediaUrlPolicy =
    policy
  def mediaUploader(using editor: Editor): Option[MediaUploader]             = editor.mediaUploader
  def mediaUrlPolicy(using editor: Editor): MediaUrlPolicy                   = editor.mediaUrlPolicy
  def mediaStatusProperty(using editor: Editor): Property[MediaUploadStatus] =
    editor.mediaStatusProperty

  def value(using editor: Editor): String = editor.valueProperty.get

  def value_=(nextValue: String)(using editor: Editor): Unit =
    editor.valueProperty.set(Option(nextValue).getOrElse(""))

  def valueProperty(using editor: Editor): Property[String] = editor.valueProperty

  def errorsProperty(using editor: Editor): ui.core.state.ListProperty[String] = editor.errors

  def toolbarMode(using editor: Editor): EditorToolbarMode = editor.toolbarMode

  def toolbarMode_=(mode: EditorToolbarMode)(using editor: Editor): Unit =
    editor.toolbarMode = mode

  def ribbonToolbar()(using editor: Editor): Unit = editor.toolbarMode = EditorToolbarMode.Ribbon

  def menuToolbar()(using editor: Editor): Unit = editor.toolbarMode = EditorToolbarMode.Menu

  def floatingToolbar()(using editor: Editor): Unit = editor.toolbarMode =
    EditorToolbarMode.Floating

  def editUrl(using editor: Editor): Option[String] = editor.editUrl

  def editUrl_=(value: String)(using editor: Editor): Unit = editor.editUrl = value

  def editLabel(using editor: Editor): String = editor.editLabel

  def editLabel_=(value: String)(using editor: Editor): Unit = editor.editLabel = value

  def readonlyUrl(using editor: Editor): Option[String] = editor.readonlyUrl

  def readonlyUrl_=(value: String)(using editor: Editor): Unit = editor.readonlyUrl = value

  def readonlyLabel(using editor: Editor): String = editor.readonlyLabel

  def readonlyLabel_=(value: String)(using editor: Editor): Unit = editor.readonlyLabel = value
}
