package ui.editor

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.list.*
import ember.editor.code.*
import ember.editor.codehighlighting.CodeDecorations
import ember.editor.table.*
import ember.editor.link.*
import ember.editor.image.{ImageExtension, ImageCommands, ImageNode, PositivePixels, MediaUrlPolicy as NativeMediaPolicy, MediaReference as NativeMediaReference, MediaId}
import ember.editor.history.*
import ember.editor.markdown.{ListKind as MarkdownListKind, *}
import ember.editor.standard.*
import ember.editor.browser.*
import ember.editor.browsersupport.*
import ember.editor.clipboard.*
import ember.editor.toolbar.*
import ember.editor.forms.{MediaCoordinator as NativeMediaCoordinator, *}
import ember.editor.ui.DocumentView
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.DomCursor
import ui.core.i18n.RuntimeMessage
import ui.core.dsl.DslLayer
import ui.viewport.Viewport
import ui.editor.plugins.EditorPlugin
import org.scalajs.dom
import scala.scalajs.js
import scala.concurrent.{Future, ExecutionContext}
import scala.util.control.NonFatal
import ember.editor.json.JsonSupport

/** A mounted editor's live Ember session, as seen by code outside this module.
  *
  * Exists for the JavaScript bridge (P29): it lends the session to TypeScript as a non-owning
  * handle. Everything here belongs to the adapter -- `session` is disposed when the visual surface
  * closes, and a borrower has to check `session.isDisposed` rather than keep a copy of anything.
  *
  * @param markdown
  *   the form's own Markdown dialect, so a borrower reads exactly the value the form submits.
  * @param json
  *   the JSON support matching the extensions this adapter installs.
  * @param links
  *   the policy [[LinkExtension]] was installed with; a link payload has to pass the same one.
  * @param history
  *   the adapter's history, for undo/redo availability.
  */
private[ui] final class NativeEditorBinding(
    val session: EditorSession,
    val markdown: FieldCodec,
    val json: JsonSupport,
    val links: LinkUrlPolicy,
    val history: History
)
