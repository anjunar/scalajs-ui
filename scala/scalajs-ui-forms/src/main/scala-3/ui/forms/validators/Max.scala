package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class Max(value: Long, message: String = "") extends StaticAnnotation
