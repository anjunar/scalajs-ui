package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class NotBlank(message: String = "") extends StaticAnnotation
