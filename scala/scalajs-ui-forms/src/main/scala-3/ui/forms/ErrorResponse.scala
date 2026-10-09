package ui.forms

final case class ErrorResponse(message: String = "", path: Seq[String] = Seq.empty) {
  def withoutHead: ErrorResponse = copy(path = path.drop(1))
}

