package ui.editor.plugins

import ui.core.component.Runtime
import ui.editor.EditorDialogForm
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class PluginDialogSpec extends AnyFlatSpec with Matchers {
  "Viewport editor form" should "render labelled fields and escape application values through UI components" in {
    val form = new EditorDialogForm(
      Vector("Adresse" -> "https://example.test/?a=1&b=2", "Titel" -> "<script>"),
      _ => Right(()),
      None,
      () => ()
    )
    val html = Runtime.renderToString(cursor => Runtime.mount(form, cursor))
    html should include("class=\"scalajs-ui-editor-dialog\"")
    html should include("<label>Adresse<input")
    html should include("https://example.test/?a=1&amp;b=2")
    html should include("&lt;script>")
    html should include("type=\"submit\"")
    html should include("Abbrechen")
    html should include("role=\"alert\"")
  }
}
