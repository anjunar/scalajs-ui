package ui.forms

import org.scalatest.funsuite.AnyFunSuite
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.render
import ui.core.i18n.{I18n, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog, i18n}
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.forms.Form.form
import ui.forms.Input.input
import ui.forms.InputContainer.inputContainer
import ui.forms.SubForm.subForm
import ui.forms.validators.*

import scala.annotation.meta.field
import reflect.Annotation

class FormValidationI18nSpec extends AnyFunSuite {
  private val de                          = I18nLocale("de")
  private def runtime(locale: I18nLocale) = I18nRuntime(
    Property(locale),
    I18nResolver(
      MessageCatalog(
        (ValidationI18n.german ++ Seq(
          I18n.entry(i18n"Name is required".key).translations(de   -> "Der Name fehlt"),
          I18n.entry(i18n"Already registered".key).translations(de -> "Bereits registriert")
        ))*
      )
    )
  )

  test(
    "annotation validation and existing server errors translate on locale changes and clear permanently"
  ) {
    val language                        = runtime(I18nLocale.En)
    var mounted: Form[ValidationPerson] = null
    var name: Input                     = null
    var email: Input                    = null
    val cursor                          = new SsrCursor()
    val root                            = Runtime.mount(
      new AbstractComponent {
        val tagName                                = "main"
        override def compose(cursor: Cursor): Unit = {
          I18nRuntime.provide(language)(using this)
          render(this, cursor) {
            mounted = form(new ValidationPerson) {
              inputContainer("Name") { name = input("name") {} }
              subForm[ValidationContact]("contact") {
                inputContainer("Email") { email = input("email") {} }
              }
            }
          }
        }
      },
      cursor
    )
    try {
      assert(mounted.validate() == Seq("Name is required", "Must be a valid email address"))
      language.setLocale(de)
      assert(name.errors.toSeq == Seq("Der Name fehlt"))
      assert(email.errors.toSeq == Seq("Bitte gib eine gültige E-Mail-Adresse ein"))
      assert(cursor.collectHtml().contains("Der Name fehlt"))

      mounted.setErrorResponses(Seq(ErrorResponse("Already registered", Seq("contact", "email"))))
      language.setLocale(I18nLocale.En)
      assert(email.errors.toSeq == Seq("Already registered"))
      language.setLocale(de)
      assert(email.errors.toSeq == Seq("Bereits registriert"))
      mounted.clearErrors()
      language.setLocale(I18nLocale.En)
      assert(name.errors.isEmpty && email.errors.isEmpty)
      assert(!cursor.collectHtml().contains("Already registered"))
    } finally Runtime.unmount(root)
  }

  test(
    "size, numeric and precision messages keep arguments separate from their translated patterns"
  ) {
    val resolver                                                  = runtime(de)
    def message[V](validator: Validator[V], candidate: V): String =
      resolver.resolveNow(validator.validateMessage(candidate).get)
    assert(
      message(
        SizeValidator[String](2, 7),
        "a"
      ) == "Muss zwischen 2 und 7 Zeichen/Einträge enthalten"
    )
    assert(
      message(SizeValidator[String](min = 3), "a") == "Muss mindestens 3 Zeichen/Einträge enthalten"
    )
    assert(
      message(
        SizeValidator[String](max = 2),
        "abc"
      ) == "Darf höchstens 2 Zeichen/Einträge enthalten"
    )
    assert(message(SizeValidator[String](2, 2), "a") == "Muss genau 2 Zeichen/Einträge enthalten")
    assert(message(MinValidator[Int](5), 4) == "Muss größer oder gleich 5 sein")
    assert(
      message(
        DecimalMaxValidator[String](BigDecimal("1.5"), inclusive = false),
        "2"
      ) == "Muss kleiner als 1.5 sein"
    )
    assert(
      message(
        DigitsValidator[String](3, 2),
        "1234"
      ) == "Höchstens 3 Vorkommastellen und 2 Nachkommastellen sind erlaubt"
    )
  }

  test("runtime string keys match macros and missing translations retain custom text") {
    assert(
      I18n.literal("Name is required").key.fingerprint == i18n"Name is required".key.fingerprint
    )
    val legacy = new Validator[String] {
      def validate(value: String) = Some("A custom validation error")
    }
    assert(runtime(de).resolveNow(legacy.validateMessage("").get) == "A custom validation error")
    assert(NotBlankValidator().validate("").contains("Must not be blank"))
    val reflected = ValidatorFactory
      .createValidator(Annotation("ui.forms.validators.NotBlank", Map("message" -> "")))
      .get
    assert(
      runtime(de).resolveNow(
        reflected.validateMessage("").get
      ) == "Dieses Feld darf nicht leer sein"
    )
  }
}
