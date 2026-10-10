package ui.forms

import ui.core.state.Property

import java.util
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

final class Media(
    val id: Property[util.UUID] = Property(MediaId.randomUuid()),
    var thumbnail: Property[Thumbnail] = Property(null),
    var name: Property[String] = Property(""),
    var contentType: Property[String] = Property(""),
    var data: Property[String] = Property("")
)
