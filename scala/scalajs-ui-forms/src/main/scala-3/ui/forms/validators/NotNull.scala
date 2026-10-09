package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class NotNull(message: String = "")   extends StaticAnnotation
