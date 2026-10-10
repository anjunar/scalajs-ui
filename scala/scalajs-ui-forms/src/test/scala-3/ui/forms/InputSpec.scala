package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import ui.core.state.Disposable
import ui.forms.Input.*
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
