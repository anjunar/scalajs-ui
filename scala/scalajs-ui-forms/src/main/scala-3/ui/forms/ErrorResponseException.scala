package ui.forms

final class ErrorResponseException(val errors: Seq[ErrorResponse])
    extends RuntimeException(errors.map(_.message).filter(_.nonEmpty).mkString(", "))
