package ui.forms

import ui.core.state.Property

import java.util

final class Thumbnail(
    val id: Property[util.UUID] = Property(MediaId.randomUuid()),
    var name: Property[String] = Property(""),
    var contentType: Property[String] = Property(""),
    var data: Property[String] = Property("")
)
