package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel

// The P29 session API. Its own object, and deliberately not touched by EditorRuntime.install():
// reaching `editorApi` registers nothing and installs no runtime, so the npm editor facade can import
// it (npm/scalajs-ui-bridge/editor-api.js) and still load where a stub runtime is installed. Same
// moduleID as the registration, so both land in the one physical editor.js chunk.
private[bridge] object EditorApiRuntime {
  @JSExportTopLevel("editorApi", "editor")
  val editorApi: EditorApiBridge = new EditorApiBridge()
}

