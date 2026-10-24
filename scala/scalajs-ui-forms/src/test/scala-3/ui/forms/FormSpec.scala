package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.{ListProperty, Property}
import ui.forms.ArrayForm.*
import ui.forms.FieldSet.fieldSet
import ui.forms.Form.form
import ui.forms.Input.input
import ui.forms.InputContainer.inputContainer
import ui.forms.SubForm.subForm
import ui.forms.validators.{NotBlank, NotBlankValidator}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.annotation.meta.field

class FormSpec extends AnyFlatSpec with Matchers {

  "Form" should "bind model properties bidirectionally and move bindings on model replacement" in {
    val first = Person()
    first.name.set("Ada")
    val second = Person()
    second.name.set("Grace")
    var mountedForm: Form[Person] = null
    var nameInput: Input          = null

    val root = mount {
      mountedForm = form(first) {
        nameInput = input("name") {}
      }
    }

    nameInput.valueProperty.get shouldBe "Ada"
    nameInput.validators.toSeq should contain(NotBlankValidator("Name is required"))

    nameInput.valueProperty.set("Augusta")
    first.name.get shouldBe "Augusta"

    mountedForm.setModel(second)
    nameInput.valueProperty.get shouldBe "Grace"
    nameInput.valueProperty.set("Hopper")
    second.name.get shouldBe "Hopper"
    first.name.get shouldBe "Augusta"

    Runtime.unmount(root)
  }

  it should "bind nested subforms and route nested server errors" in {
    val person = Person()
    person.address.get.street.set("Analytical Engine Way")
    var mountedForm: Form[Person] = null
    var streetInput: Input        = null

    val root = mount {
      mountedForm = form(person) {
        subForm[Address]("address") {
          streetInput = input("street") {}
        }
      }
    }

    streetInput.valueProperty.get shouldBe "Analytical Engine Way"
    streetInput.valueProperty.set("Compiler Street")
    person.address.get.street.get shouldBe "Compiler Street"

    streetInput.valueProperty.set("")
    mountedForm.validate() should contain("Street is required")

    mountedForm.setErrorResponses(Seq(ErrorResponse("Unknown street", Seq("address", "street"))))
    streetInput.errors.toSeq shouldBe Seq("Unknown street")

    mountedForm.resetInteractionState()
    streetInput.errors shouldBe empty
    streetInput.dirtyProperty.get shouldBe false

    Runtime.unmount(root)
  }

  it should "render and synchronize array controls across structural list changes" in {
    val person = Person()
    person.tags.setAll(Seq("math", "compilers"))
    var tagsForm: ArrayForm[String] = null

    val root = mount {
      form(person) {
        tagsForm = arrayForm[String]("tags") {
          controlRenderer = index => input(s"tag-$index") {}
        }
      }
    }

    tagsForm.itemControls.map(_.valueProperty.get) shouldBe Seq("math", "compilers")
    tagsForm.itemControls.foreach { control =>
      control.asInstanceOf[AbstractComponent].parent should not contain tagsForm
    }

    person.tags.insert(1, "logic")
    tagsForm.itemControls.map(_.valueProperty.get) shouldBe Seq("math", "logic", "compilers")

    tagsForm.itemControls.head.asInstanceOf[Input].valueProperty.set("algebra")
    person.tags.get.toSeq shouldBe Seq("algebra", "logic", "compilers")

    Runtime.unmount(root)
  }

  it should "bind the controls grouped in a FieldSet to the model and route errors to them" in {
    val person = Person()
    person.name.set("Ada")
    var mountedForm: Form[Person] = null
    var group: FieldSet           = null
    var nameInput: Input          = null

    val root = mount {
      mountedForm = form(person) {
        group = fieldSet("identity") {
          nameInput = input("name") {}
        }
      }
    }

    mountedForm.validateBindings() shouldBe empty
    mountedForm.fields.keySet.toSeq shouldBe Seq("identity", "name")
    group.fields.keySet.toSeq shouldBe Seq("name")
    nameInput.valueProperty.get shouldBe "Ada"
    nameInput.valueProperty.set("Augusta")
    person.name.get shouldBe "Augusta"

    nameInput.valueProperty.set("")
    mountedForm.validate() shouldBe Seq("Name is required")

    mountedForm.setErrorResponses(Seq(ErrorResponse("Taken", Seq("name"))))
    nameInput.errors.toSeq shouldBe Seq("Taken")
    mountedForm.setErrorResponses(Seq(ErrorResponse("Taken in group", Seq("identity", "name"))))
    nameInput.errors.toSeq shouldBe Seq("Taken in group")

    group.editable = false
    nameInput.editableProperty.get shouldBe false

    Runtime.unmount(root)
    mountedForm.fields shouldBe empty
    group.fields shouldBe empty
  }

  it should "deliver errors addressed to containers and to the form itself" in {
    val person = Person()
    person.tags.setAll(Seq("math"))
    var mountedForm: Form[Person] = null
    var address: SubForm[Address] = null
    var tags: ArrayForm[String]   = null

    val root = mount {
      mountedForm = form(person) {
        address = subForm[Address]("address") {
          input("street") {}
        }
        tags = arrayForm[String]("tags") {
          controlRenderer = index => input(s"tag-$index") {}
        }
      }
    }

    mountedForm.setErrorResponses(
      Seq(
        ErrorResponse("Address incomplete", Seq("address")),
        ErrorResponse("Too many tags", Seq("tags")),
        ErrorResponse("Unknown tag", Seq("tags", "0")),
        ErrorResponse("Form rejected", Seq())
      )
    )
    address.errors.toSeq shouldBe Seq("Address incomplete")
    tags.errors.toSeq shouldBe Seq("Too many tags")
    tags.itemControls.head.errors.toSeq shouldBe Seq("Unknown tag")
    mountedForm.errors.toSeq shouldBe Seq("Form rejected")

    mountedForm.clearErrors()
    address.errors shouldBe empty
    tags.errors shouldBe empty
    tags.itemControls.head.errors shouldBe empty
    mountedForm.errors shouldBe empty

    Runtime.unmount(root)
  }

  it should "keep a server error on an untouched control until the user edits it" in {
    val person = Person()
    person.name.set("Ada")
    var nameInput: Input = null

    val root = mount {
      form(person) {
        nameInput = input("name") {}
      }
    }

    nameInput.setErrors(Seq("Already registered"))
    nameInput.validate()
    nameInput.errors.toSeq shouldBe Seq("Already registered")

    // Focus and blur without typing is not an edit either.
    nameInput.dirtyProperty.set(true)
    nameInput.validate()
    nameInput.errors.toSeq shouldBe Seq("Already registered")

    nameInput.valueProperty.set("")
    nameInput.errors.toSeq shouldBe Seq("Name is required")

    Runtime.unmount(root)
  }

  it should "connect InputContainer labels, state classes, and validation messages" in {
    var container: InputContainer = null
    var nameInput: Input          = null
    val label                     = Property("Name")

    val root = mount {
      container = inputContainer(label) {
        nameInput = input("name", standalone = true) {
          Input.validators += NotBlankValidator("Required")
        }
      }
    }

    nameInput.host.attribute("placeholder") shouldBe Some("Name")
    container.host.attribute("class").getOrElse("") should include("empty")

    nameInput.validate(forceVisible = true)
    descendantText(container) should contain("Required")

    nameInput.valueProperty.set("Ada")
    container.host.attribute("class").getOrElse("") should not include "empty"

    label.set("Display name")
    nameInput.host.attribute("placeholder") shouldBe Some("Display name")
    descendantText(container) should contain("Display name")

    Runtime.unmount(root)
  }

  private def mount(body: AbstractComponent ?=> Cursor ?=> Unit): TestRoot = {
    val root = new TestRoot(body)
    Runtime.mount(root, new SsrCursor())
  }

  private def descendantText(component: AbstractComponent): Seq[String] =
    component.children.flatMap {
      case value: TextComponent => Seq(value.getText)
      case child                => descendantText(child)
    }
}
