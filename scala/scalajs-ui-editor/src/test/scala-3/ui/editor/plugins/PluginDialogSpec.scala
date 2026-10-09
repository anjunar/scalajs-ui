package ui.editor.plugins

import ui.core.component.{AbstractCustomComponent, Runtime}
import ui.editor.{EditorDialogForm, EditorMessages, EditorText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class PluginDialogSpec extends AnyFlatSpec with Matchers {
  "Viewport editor form" should "render labelled fields and escape application values through UI components" in {
    val form = new EditorDialogForm(
      EditorText(new AbstractCustomComponent {}),
      Vector(
        EditorMessages.address -> "https://example.test/?a=1&b=2",
        EditorMessages.title   -> "<script>"
      ),
      _ => Right(()),
      None,
      () => ()
    )
    val html = Runtime.renderToString(cursor => Runtime.mount(form, cursor))
    html should include("class=\"scalajs-ui-editor-dialog\"")
    html should include("<label>Address<input")
    html should include("https://example.test/?a=1&amp;b=2")
    html should include("&lt;script>")
    html should include("type=\"submit\"")
    html should include("Cancel")
    html should include("role=\"alert\"")
  }
}
