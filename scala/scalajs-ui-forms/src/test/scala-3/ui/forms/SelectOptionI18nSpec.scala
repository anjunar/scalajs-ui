package ui.forms

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.i18n.{I18n, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog, i18n}
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.forms.SelectInput.selectInput

class SelectOptionI18nSpec extends AnyFlatSpec with Matchers {
  "SelectOption" should "translate direct and reactive messages without changing the selected value" in {
    val german  = I18nLocale("de")
    val runtime = I18nRuntime(
      Property(I18nLocale.En),
      I18nResolver(
        MessageCatalog(
          I18n.entry(i18n"Draft".key).translations(german     -> "Entwurf"),
          I18n.entry(i18n"Published".key).translations(german -> "Veröffentlicht")
        )
      )
    )
    val published            = Property(false)
    val cursor               = new SsrCursor()
    var control: SelectInput = null
    val root                 = Runtime.mount(
      new AbstractComponent {
        val tagName                                = "main"
        override def compose(cursor: Cursor): Unit = {
          I18nRuntime.provide(runtime)(using this)
          render(this, cursor) {
            control = selectInput(
              "status",
              Seq(
                SelectOption(
                  "current",
                  published.map(value => if (value) i18n"Published" else i18n"Draft")
                ),
                SelectOption("published", i18n"Published")
              ),
              standalone = true
            ) {}
          }
        }
      },
      cursor
    )
    control.valueProperty.set("current")
    cursor.collectHtml() should include(
      "<option value=\"current\" selected=\"selected\">Draft</option>"
    )

    runtime.setLocale(german)
    published.set(true)
    cursor.collectHtml() should include(
      "<option value=\"current\" selected=\"selected\">Veröffentlicht</option>"
    )
    cursor.collectHtml() should include("<option value=\"published\">Veröffentlicht</option>")
    control.valueProperty.get shouldBe "current"
    Runtime.unmount(root)
  }

  it should "accept plain labels and preserve existing property labels without an i18n runtime" in {
    val label  = Property("Data")
    val cursor = new SsrCursor()
    val root   = Runtime.mount(
      new AbstractComponent {
        val tagName                                = "main"
        override def compose(cursor: Cursor): Unit =
          render(this, cursor) {
            val existing = SelectOption("data", label)
            (existing.label eq label) shouldBe true
            selectInput("plain", Seq(SelectOption("all", "All"), existing), standalone = true) {}
          }
      },
      cursor
    )
    label.set("Changed")
    cursor.collectHtml() should include("<option value=\"all\">All</option>")
    cursor.collectHtml() should include("<option value=\"data\">Changed</option>")
    Runtime.unmount(root)
  }
}
