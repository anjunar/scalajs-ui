package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class PastOrPresent(message: String = "")
    extends StaticAnnotation
