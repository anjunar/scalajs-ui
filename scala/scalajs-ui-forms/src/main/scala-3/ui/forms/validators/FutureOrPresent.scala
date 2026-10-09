package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class FutureOrPresent(message: String = "")
    extends StaticAnnotation
