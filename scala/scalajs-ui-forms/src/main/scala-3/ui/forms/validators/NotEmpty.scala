package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class NotEmpty(message: String = "") extends StaticAnnotation
