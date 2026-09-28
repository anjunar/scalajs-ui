package ui.core.i18n

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ui.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import ui.core.dsl.AttributeDsl
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.DslLayer.render
import ui.core.layout.Button.button
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.core.text.TextValue

import scala.collection.mutable.ArrayBuffer

class I18nDslSpec extends AnyFlatSpec with Matchers {
  private val german = I18nLocale("de")
  private val catalog = MessageCatalog(
    I18n.entry(i18n"Save".key).translations(german -> "Speichern"),
    I18n.entry(i18n"Saving".key).translations(german -> "Wird gespeichert"),
    I18n.entry(i18n"Choose an author".key).translations(german -> "Autor auswählen")
  )

  "The text DSL" should "bind direct and changing messages through the inherited runtime" in {
    val runtime = I18nRuntime(Property(I18nLocale.En), I18nResolver(catalog))
    val busy = Property(false)
    val message = busy.map(value => if (value) i18n"Saving" else i18n"Save")
    val cursor = new SsrCursor()
    val root = Runtime.mount(new AbstractComponent {
      val tagName = "main"
      override def compose(cursor: Cursor): Unit = {
        I18nRuntime.provide(runtime)(using this)
        render(this, cursor) {
          div {
            ariaLabel = i18n"Save"
            title = message
            placeholder = i18n"Choose an author"
            AttributeDsl.setAttribute("data-message", message)
            text(message) {}
            button(message) {}
          }
        }
      }
    }, cursor)

    cursor.collectHtml() should include("aria-label=\"Save\"")
    cursor.collectHtml() should include("placeholder=\"Choose an author\"")
    cursor.collectHtml() should include("title=\"Save\"")
    cursor.collectHtml() should include("data-message=\"Save\"")

    busy.set(true)
    runtime.setLocale(german)
    val html = cursor.collectHtml()
    html should include("aria-label=\"Speichern\"")
    html should include("placeholder=\"Autor auswählen\"")
    html should include("title=\"Wird gespeichert\"")
    html should include("data-message=\"Wird gespeichert\"")
    html should include(">Wird gespeichert<button>Wird gespeichert</button>")

    busy.set(false)
    cursor.collectHtml() should include(">Speichern<button>Speichern</button>")
    Runtime.unmount(root)
  }

  it should "replace inner subscriptions and stop updating after disposal" in {
    given AbstractComponent = new AbstractCustomComponent {}
    val runtime = I18nRuntime(Property(I18nLocale.En), I18nResolver(catalog))
    I18nRuntime.provide(runtime)
    val busy = Property(false)
    val value = TextValue.asReadOnlyProperty(busy.map(value => if (value) i18n"Saving" else i18n"Save"))
    val seen = ArrayBuffer.empty[String]
    val subscription = value.observe(seen += _)
    busy.set(true)
    runtime.setLocale(german)
    busy.set(false)
    runtime.setLocale(I18nLocale.En)
    seen.toSeq shouldBe Seq("Save", "Saving", "Wird gespeichert", "Speichern", "Save")

    subscription.dispose()
    busy.set(true)
    runtime.setLocale(german)
    seen.size shouldBe 5
  }

  it should "preserve string properties without requiring an i18n context" in {
    given AbstractComponent = new AbstractCustomComponent {}
    val source = Property("Untranslated data")
    val adapted = TextValue.asReadOnlyProperty(source)
    (adapted eq source) shouldBe true
    source.set("New data")
    adapted.get shouldBe "New data"
  }
}
