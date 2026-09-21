package ui.json

import scala.annotation.StaticAnnotation

class JsonProperty(val value: String = "", val model: Class[?] = classOf[Object]) extends StaticAnnotation {
  def this(model: Class[?]) = this("", model)
}
