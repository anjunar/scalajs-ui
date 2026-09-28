package ui.editor

import ui.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import ui.core.dsl.DslLayer.{child, render}
import ui.core.i18n.{I18n, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog}
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class EditorI18nSpec extends AnyFlatSpec with Matchers {
  private def runtime(): I18nRuntime = I18nRuntime(
    Property(I18nLocale.En),
    I18nResolver(
      MessageCatalog(
        I18n.entry(EditorMessages.visual.key).translations(I18nLocale("de")   -> "Visuell"),
        I18n.entry(EditorMessages.readonly.key).translations(I18nLocale("de") -> "Lesemodus"),
        I18n.entry(EditorMessages.toolbar.key).translations(I18nLocale("de")  -> "Werkzeugleiste"),
        I18n
          .entry(EditorMessages.uploading(0).key)
          .translations(I18nLocale("de") -> "{count} Bilder werden hochgeladen"),
        I18n.entry(EditorMessages.address.key).translations(I18nLocale("de") -> "Adresse"),
        I18n.entry(EditorMessages.apply.key).translations(I18nLocale("de")   -> "Übernehmen"),
        I18n.entry(EditorMessages.cancel.key).translations(I18nLocale("de")  -> "Abbrechen")
      )
    )
  )

  "Editor messages" should "fall back to English without an application runtime" in {
    val labels = EditorText(new AbstractCustomComponent {})
    labels.now(EditorMessages.undo) shouldBe "Undo"
    labels.now(EditorMessages.uploading(3)) shouldBe "Uploading 3 image(s)…"
    labels.now(EditorMessages.markdownFallback("Unsupported content")) shouldBe
      "Markdown view: Unsupported content"
  }

  it should "translate server-rendered controls and pending counts without changing Markdown" in {
    val language        = runtime()
    val cursor          = new SsrCursor()
    var control: Editor = null
    val root            = Runtime.mount(
      new AbstractComponent {
        val tagName                                = "main"
        override def compose(cursor: Cursor): Unit = render(this, cursor) {
          I18nRuntime.provide(language)
          control = Editor.editor("body", standalone = true) {
            Editor.value = "**Keep this**"
            Editor.markdownMode = true
          }
        }
      },
      cursor
    )
    try {
      control.mediaStatusProperty.set(MediaUploadStatus(pending = 2))
      cursor.collectHtml() should include("Uploading 2 image(s)…")
      cursor.collectHtml() should include(">Visual</button>")
      language.setLocale(I18nLocale("de"))
      val translated = cursor.collectHtml()
      translated should include(">Visuell</button>")
      translated should include(">Lesemodus</a>")
      translated should include("aria-label=\"Werkzeugleiste\"")
      translated should include("2 Bilder werden hochgeladen")
      control.valueProperty.get shouldBe "**Keep this**"
      control.mediaStatusProperty.set(MediaUploadStatus(pending = 4))
      cursor.collectHtml() should include("4 Bilder werden hochgeladen")
    } finally Runtime.unmount(root)
    // Locale subscriptions belong to mounted components.
    language.setLocale(I18nLocale.En)
    cursor.collectHtml() shouldBe ""
  }

  it should "keep explicit application mode labels" in {
    val language = runtime()
    language.setLocale(I18nLocale("de"))
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new AbstractComponent {
          val tagName                                = "main"
          override def compose(cursor: Cursor): Unit = render(this, cursor) {
            I18nRuntime.provide(language)
            Editor.editor("body", standalone = true) {
              Editor.readonlyLabel = "Application preview"
            }
          }
        },
        cursor
      )
    }
    html should include(">Application preview</a>")
    html should not include ">Lesemodus</a>"
  }

  it should "translate a detached dialog through its owner and retain field values" in {
    val language = runtime()
    val cursor   = new SsrCursor()
    val form     = new EditorDialogForm(
      new EditorText(language),
      Vector(EditorMessages.address -> "https://example.test/?x=<value>"),
      _ => Right(()),
      None,
      () => ()
    )
    Runtime.mount(form, cursor)
    try {
      cursor.collectHtml() should include(">Apply</button>")
      language.setLocale(I18nLocale("de"))
      val html = cursor.collectHtml()
      html should include("<label>Adresse<input")
      html should include(">Übernehmen</button>")
      html should include(">Abbrechen</button>")
      html should include("https://example.test/?x=&lt;value>")
    } finally Runtime.unmount(form)
  }
}
