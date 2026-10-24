package ui.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.Annotation
import scala.scalajs.js

class BuiltinValidatorsSpec extends AnyFlatSpec with Matchers {

  "NotBlankValidator" should "reject null, empty, and whitespace-only values" in {
    val validator = NotBlankValidator()

    validator.validate(null) shouldBe Some("Must not be blank")
    validator.validate("") shouldBe Some("Must not be blank")
    validator.validate("  ") shouldBe Some("Must not be blank")
    validator.validate("Mira") shouldBe None
  }

  "SizeValidator" should "keep null values optional and enforce its configured range" in {
    val validator = SizeValidator[String](min = 2, max = 4)

    validator.validate(null) shouldBe None
    validator.validate("A") shouldBe Some("Must contain between 2 and 4 characters/items")
    validator.validate("Mira") shouldBe None
    validator.validate("Patrick") shouldBe Some("Must contain between 2 and 4 characters/items")
  }

  "EmailValidator" should "allow optional empty values and reject malformed addresses" in {
    val validator = EmailValidator()

    validator.validate("") shouldBe None
    validator.validate("invalid") shouldBe Some("Must be a valid email address")
    validator.validate("mira@example.org") shouldBe None
  }

  "Decimal and digit validators" should "enforce inclusive bounds and precision" in {
    DecimalMinValidator[String](BigDecimal("1.5")).validate("1.49") should not be empty
    DecimalMinValidator[String](BigDecimal("1.5")).validate("1.5") shouldBe None
    DecimalMaxValidator[Double](BigDecimal("2.5"), inclusive = false).validate(
      2.5
    ) should not be empty
    DigitsValidator[String](integer = 3, fraction = 2).validate("123.45") shouldBe None
    DigitsValidator[String](integer = 3, fraction = 2).validate("1234.5") should not be empty
  }

  it should "read numeric strings exactly and treat non-numbers as absent" in {
    val min        = MinValidator[String](1)
    val decimalMin = DecimalMinValidator[String](BigDecimal(1))
    val max        = DecimalMaxValidator[String](BigDecimal("12345678901234567890"))

    noException should be thrownBy min.validate("1e400")
    min.validate("1e400") should not be empty
    decimalMin.validate("1e400") shouldBe None
    Seq("NaN", "Infinity", "-Infinity", "abc").foreach { text =>
      min.validate(text) shouldBe None
      decimalMin.validate(text) shouldBe None
    }
    max.validate("12345678901234567890") shouldBe None
    max.validate("12345678901234567891") should not be empty
  }

  "Temporal validators" should "compare instants against the current time" in {
    PastValidator[Instant]().validate(Instant.now().minusSeconds(10)) shouldBe None
    FutureValidator[Instant]().validate(Instant.now().plusSeconds(10)) shouldBe None
    PastValidator[Instant]().validate(Instant.now().plusSeconds(10)) should not be empty
  }

  it should "validate HTML date input strings with local-date boundaries" in {
    val now   = new js.Date()
    val today = LocalDate.of(now.getFullYear().toInt, now.getMonth().toInt + 1, now.getDate().toInt)
    val yesterday  = today.minusDays(1).toString
    val tomorrow   = today.plusDays(1).toString
    val validators = Seq(
      (PastValidator[String](), Seq(true, false, false)),
      (PastOrPresentValidator[String](), Seq(true, true, false)),
      (FutureValidator[String](), Seq(false, false, true)),
      (FutureOrPresentValidator[String](), Seq(false, true, true))
    )

    validators.foreach { (validator, expected) =>
      Seq(yesterday, today.toString, tomorrow).map(validator.validate(_).isEmpty) shouldBe expected
      validator.validate("") shouldBe None
      validator.validate(null) shouldBe None
      validator.validate("not a date") should not be empty
      validator.validate("2026-02-30") should not be empty
    }
    PastOrPresentValidator[LocalDate]().validate(today) shouldBe None
    FutureOrPresentValidator[LocalDate]().validate(today) shouldBe None
  }

  it should "validate datetime-local and offset strings against the current time" in {
    val now   = new js.Date()
    val local = LocalDateTime.of(
      now.getFullYear().toInt,
      now.getMonth().toInt + 1,
      now.getDate().toInt,
      now.getHours().toInt,
      now.getMinutes().toInt,
      now.getSeconds().toInt
    )
    val pastLocal    = local.minusHours(1).toString
    val futureLocal  = local.plusHours(1).toString
    val pastOffset   = Instant.now().minusSeconds(3600).toString
    val futureOffset = Instant.now().plusSeconds(3600).toString

    PastValidator[String]().validate(pastLocal) shouldBe None
    PastValidator[String]().validate(futureLocal) should not be empty
    FutureValidator[String]().validate(futureLocal) shouldBe None
    FutureValidator[String]().validate(pastLocal) should not be empty
    PastOrPresentValidator[String]().validate(pastLocal) shouldBe None
    FutureOrPresentValidator[String]().validate(futureLocal) shouldBe None

    PastValidator[String]().validate(pastOffset) shouldBe None
    PastValidator[String]().validate(futureOffset) should not be empty
    FutureValidator[String]().validate(futureOffset) shouldBe None
  }

  "ValidatorFactory" should "create configured validators from reflection annotations" in {
    val validator = ValidatorFactory.createValidator(
      Annotation(
        "ui.forms.validators.Size",
        Map("min" -> 2, "max" -> 3, "message" -> "Wrong size")
      )
    )

    validator.get.validate("A") shouldBe Some("Wrong size")
    validator.get.validate("Ada") shouldBe None
    ValidatorFactory.createValidator(Annotation("unknown.Annotation")) shouldBe None
  }
}
