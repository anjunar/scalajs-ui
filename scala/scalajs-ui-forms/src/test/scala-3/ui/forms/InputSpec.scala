package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import ui.core.state.{Disposable, Property}
import ui.forms.Input.*
import ui.forms.TextAreaInput.textAreaInput
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class InputSpec extends AnyFlatSpec with Matchers {

  "Input" should "treat an undefined native value as empty during input events" in {
    var control: Input             = null
    var inputHost: TestHostElement = null
    val cursor = new TestCursor(host => if (host.tagName == "input") inputHost = host)
    val root   = new InputRoot {
      override protected def content(using AbstractComponent, Cursor): Unit =
        control = input("email", standalone = true) {}
    }
    Runtime.mount(root, cursor)

    inputHost.setProperty("value", js.undefined)
    noException should be thrownBy inputHost.fire(
      "input",
      js.Dynamic.literal(target = js.undefined)
    )
    control.valueProperty.get shouldBe ""

    Runtime.unmount(root)
  }

  "Hydrating form controls" should "keep an edit made before hydration and push it to the model" in {
    val model =
      new ControlModel(Property("Ada"), Property("First line"), Property("CLUB"), Property(true))
    val cursor = new HydratingTestCursor(
      host =>
        if (host.tagName == "input") {
          host.setAttribute("value", "Ada")
          host.setProperty("value", "Ada Lovelace")
        },
      new TestTextAreaContent(value = "First line, edited", defaultValue = "First line")
    )
    var text: Input         = null
    var area: TextAreaInput = null
    val root                = new InputRoot {
      override protected def content(using AbstractComponent, Cursor): Unit =
        Form.form(model) {
          text = input("name") {}
          area = textAreaInput("notes") {}
        }
    }
    Runtime.mount(root, cursor)

    text.valueProperty.get shouldBe "Ada Lovelace"
    model.name.get shouldBe "Ada Lovelace"
    text.dirtyProperty.get shouldBe true
    area.valueProperty.get shouldBe "First line, edited"
    model.notes.get shouldBe "First line, edited"
    area.dirtyProperty.get shouldBe true

    Runtime.unmount(root)
  }

  it should "take the model value for a control the user did not touch" in {
    val model =
      new ControlModel(Property("Grace"), Property("Updated"), Property("CLUB"), Property(true))
    val cursor = new HydratingTestCursor(
      host =>
        if (host.tagName == "input") {
          host.setAttribute("value", "Ada")
          host.setProperty("value", "Ada")
        },
      new TestTextAreaContent(value = "First line", defaultValue = "First line")
    )
    var text: Input         = null
    var area: TextAreaInput = null
    val root                = new InputRoot {
      override protected def content(using AbstractComponent, Cursor): Unit =
        Form.form(model) {
          text = input("name") {}
          area = textAreaInput("notes") {}
        }
    }
    Runtime.mount(root, cursor)

    text.valueProperty.get shouldBe "Grace"
    text.dirtyProperty.get shouldBe false
    area.valueProperty.get shouldBe "Updated"
    area.dirtyProperty.get shouldBe false
    model.name.get shouldBe "Grace"
    model.notes.get shouldBe "Updated"

    Runtime.unmount(root)
  }

  it should "synchronize readonly state when editability changes" in {
    var inputHost: TestHostElement = null
    val cursor = new TestCursor(host => if (host.tagName == "input") inputHost = host)
    val root   = new InputRoot {
      override protected def content(using AbstractComponent, Cursor): Unit =
        input("name", standalone = true) {
          editable = false
        }
    }
    Runtime.mount(root, cursor)

    inputHost.property[Boolean]("readOnly") shouldBe Some(true)

    Runtime.unmount(root)
  }
}
