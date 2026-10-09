package ui.forms.validators

import reflect.Annotation

import scala.util.matching.Regex

object ValidatorFactory {

  def createValidators(annotations: Array[Annotation]): Vector[Validator[Any]] =
    annotations.iterator.flatMap(createValidator).toVector

  def createValidator(annotation: Annotation): Option[Validator[Any]] = {
    val parameters                      = annotation.parameters
    val validator: Option[Validator[?]] = annotation.annotationClassName match {
      case "ui.forms.validators.NotNull" =>
        Some(NotNullValidator[Any](optionalMessage(parameters)))
      case "ui.forms.validators.Null" =>
        Some(NullValidator[Any](optionalMessage(parameters)))
      case "ui.forms.validators.AssertTrue" =>
        Some(AssertTrueValidator(optionalMessage(parameters)))
      case "ui.forms.validators.AssertFalse" =>
        Some(AssertFalseValidator(optionalMessage(parameters)))
      case "ui.forms.validators.NotEmpty" =>
        Some(NotEmptyValidator[Any](optionalMessage(parameters)))
      case "ui.forms.validators.NotBlank" =>
        Some(NotBlankValidator(optionalMessage(parameters)))
      case "ui.forms.validators.Size" =>
        Some(
          SizeValidator[Any](
            int(parameters, "min", 0),
            int(parameters, "max", Int.MaxValue),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.Min" =>
        Some(MinValidator[Any](long(parameters, "value", 0L), optionalMessage(parameters)))
      case "ui.forms.validators.Max" =>
        Some(MaxValidator[Any](long(parameters, "value", 0L), optionalMessage(parameters)))
      case "ui.forms.validators.DecimalMin" =>
        Some(
          DecimalMinValidator[Any](
            BigDecimal(string(parameters, "value", "0")),
            boolean(parameters, "inclusive", true),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.DecimalMax" =>
        Some(
          DecimalMaxValidator[Any](
            BigDecimal(string(parameters, "value", "0")),
            boolean(parameters, "inclusive", true),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.Positive" =>
        Some(PositiveValidator[Any](optionalMessage(parameters)))
      case "ui.forms.validators.PositiveOrZero" =>
        Some(
          PositiveOrZeroValidator[Any](optionalMessage(parameters))
        )
      case "ui.forms.validators.Negative" =>
        Some(NegativeValidator[Any](optionalMessage(parameters)))
      case "ui.forms.validators.NegativeOrZero" =>
        Some(
          NegativeOrZeroValidator[Any](optionalMessage(parameters))
        )
      case "ui.forms.validators.Digits" =>
        Some(
          DigitsValidator[Any](
            int(parameters, "integer", 0),
            int(parameters, "fraction", 0),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.Pattern" =>
        Some(
          PatternValidator(
            new Regex(string(parameters, "regex", "")),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.EmailConstraint" =>
        Some(EmailValidator(optionalMessage(parameters)))
      case "ui.forms.validators.Past" =>
        Some(PastValidator[Any](optionalMessage(parameters)))
      case "ui.forms.validators.PastOrPresent" =>
        Some(
          PastOrPresentValidator[Any](
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.Future" =>
        Some(FutureValidator[Any](optionalMessage(parameters)))
      case "ui.forms.validators.FutureOrPresent" =>
        Some(
          FutureOrPresentValidator[Any](
            optionalMessage(parameters)
          )
        )
      case _ => None
    }

    validator.map(_.asInstanceOf[Validator[Any]])
  }

  private def optionalMessage(parameters: Map[String, Any]): String | scala.Null =
    parameters.get("message").map(_.toString).filter(_.nonEmpty).orNull

  private def string(parameters: Map[String, Any], name: String, default: String): String =
    parameters.get(name).map(_.toString).getOrElse(default)

  private def int(parameters: Map[String, Any], name: String, default: Int): Int =
    parameters.get(name).collect { case number: Number => number.intValue() }.getOrElse(default)

  private def long(parameters: Map[String, Any], name: String, default: Long): Long =
    parameters.get(name).collect { case number: Number => number.longValue() }.getOrElse(default)

  private def boolean(parameters: Map[String, Any], name: String, default: Boolean): Boolean =
    parameters.get(name).collect { case value: Boolean => value }.getOrElse(default)
}
