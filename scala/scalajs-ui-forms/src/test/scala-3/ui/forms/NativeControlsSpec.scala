package ui.forms

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.forms.validators.NotBlankValidator

class NativeControlsSpec extends AnyFlatSpec with Matchers {

  "Native form controls" should "register with a form and bind external properties in both directions" in {
    val name                     = Property("Ada")
    val notes                    = Property("First line\nSecond line")
    val kind                     = Property("CLUB")
    val published                = Property(true)
    val model                    = new ControlModel(name, notes, kind, published)
    val cursor                   = new SsrCursor()
    var form: Form[ControlModel] = null
    var text: Input              = null
    var area: TextAreaInput      = null
    var select: SelectInput      = null
    var check: CheckboxInput     = null

    val root = Runtime.mount(
      new ControlRoot {
        override protected def content(using AbstractComponent, Cursor): Unit = {
          form = Form.form(model) {
            text = Input.input("name") {}
            area = TextAreaInput.textAreaInput("notes") {}
            select = SelectInput.selectInput(
              "kind",
              Seq(
                SelectOption("CLUB", Property("Club")),
                SelectOption("FESTIVAL", Property("Festival"))
              )
            ) {}
            check = CheckboxInput.checkboxInput("published") {}
          }
        }
      },
      cursor
    )

    form.fields.keySet.toSeq shouldBe Seq("name", "notes", "kind", "published")
    text.valueProperty.get shouldBe "Ada"
    area.valueProperty.get shouldBe "First line\nSecond line"
    select.valueProperty.get shouldBe "CLUB"
    check.valueProperty.get shouldBe true
    cursor.collectHtml() should include(
      "<textarea name=\"notes\">First line\nSecond line</textarea>"
    )
    cursor.collectHtml() should include(
      "<option value=\"CLUB\" selected=\"selected\">Club</option>"
    )
    check.host.attribute("checked") shouldBe Some("checked")

    name.set("Grace")
    notes.set("Updated")
    kind.set("FESTIVAL")
    published.set(false)
    text.valueProperty.get shouldBe "Grace"
    area.valueProperty.get shouldBe "Updated"
    select.valueProperty.get shouldBe "FESTIVAL"
    check.valueProperty.get shouldBe false
    cursor.collectHtml() should include(
      "<option value=\"FESTIVAL\" selected=\"selected\">Festival</option>"
    )
    check.host.attribute("checked") shouldBe None

    text.valueProperty.set("Augusta")
    area.valueProperty.set("Another note")
    select.valueProperty.set("CLUB")
    check.valueProperty.set(true)
    name.get shouldBe "Augusta"
    notes.get shouldBe "Another note"
    kind.get shouldBe "CLUB"
    published.get shouldBe true

    Runtime.unmount(root)
    form.fields shouldBe empty
  }

  it should "apply editability and validation to text areas, selects and checkboxes" in {
    val source               = Property("Initial")
    var area: TextAreaInput  = null
    var select: SelectInput  = null
    var check: CheckboxInput = null

    val root = Runtime.mount(
      new ControlRoot {
        override protected def content(using AbstractComponent, Cursor): Unit = {
          area = TextAreaInput.textAreaInput("notes", standalone = true) {}
          area.bindValue(source)
          select = SelectInput.selectInput(
            "kind",
            Seq(
              SelectOption("A", Property("A"))
            ),
            standalone = true
          ) {}
          check = CheckboxInput.checkboxInput("published", standalone = true) {}
        }
      },
      new SsrCursor()
    )

    area.valueProperty.get shouldBe "Initial"
    source.set("")
    area.validators += NotBlankValidator("Required")
    area.validate(forceVisible = true) shouldBe Seq("Required")
    area.valueProperty.set("Ready")
    source.get shouldBe "Ready"
    area.validate(forceVisible = true) shouldBe empty

    area.editable = false
    select.editable = false
    check.editable = false
    area.host.attribute("readOnly") shouldBe Some("readOnly")
    select.host.attribute("disabled") shouldBe Some("disabled")
    check.host.attribute("disabled") shouldBe Some("disabled")

    Runtime.unmount(root)
  }
}

private abstract class ControlRoot extends AbstractComponent {
  val tagName = "div"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) { content }
}

private final class ControlModel(
    var name: Property[String],
    var notes: Property[String],
    var kind: Property[String],
    var published: Property[Boolean]
)
