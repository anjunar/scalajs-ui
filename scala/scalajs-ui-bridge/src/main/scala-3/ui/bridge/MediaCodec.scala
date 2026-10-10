package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.on
import ui.core.render.Cursor
import ui.core.state.{CompositeDisposable, Disposable as CoreDisposable, ListProperty as CoreListProperty, Property as CoreProperty}
import ui.forms.*
import ui.forms.Form.FormContext
import ui.forms.validators.{Validator, ValidatorFactory}
import org.scalajs.dom
import reflect.Annotation

import scala.collection.mutable
import scala.scalajs.js
import java.util

/** Converts between `ui.forms.Media` (a Scala class of `Property`-wrapped fields, built for the
  * Scala.js-only `ImageCropper` UI) and the plain JSON-shaped value a TS model field can actually
  * hold. Every other control's value type is already JS-compatible end to end (`js.Any`, `String`);
  * `Media` is the one exception, so it is the one place this bridge translates a value instead of
  * passing it through.
  */
private[bridge] object MediaCodec {

  def toJs(media: Media): js.Any =
    if (media == null) null
    else
      js.Dictionary[js.Any](
        "id"          -> media.id.get.toString,
        "name"        -> media.name.get,
        "contentType" -> media.contentType.get,
        "data"        -> media.data.get,
        "thumbnail"   -> Option(media.thumbnail.get).map(thumbnailToJs).orNull
      )

  private def thumbnailToJs(thumbnail: Thumbnail): js.Any =
    js.Dictionary[js.Any](
      "id"          -> thumbnail.id.get.toString,
      "name"        -> thumbnail.name.get,
      "contentType" -> thumbnail.contentType.get,
      "data"        -> thumbnail.data.get
    )

  def fromJs(value: js.Any): Media =
    if (value == null || js.isUndefined(value)) null
    else {
      val dict = value.asInstanceOf[js.Dictionary[js.Any]]
      new Media(
        id = CoreProperty(util.UUID.fromString(stringField(dict, "id"))),
        name = CoreProperty(stringField(dict, "name")),
        contentType = CoreProperty(stringField(dict, "contentType")),
        data = CoreProperty(stringField(dict, "data")),
        thumbnail = CoreProperty(
          dict
            .get("thumbnail")
            .filter(value => value != null && !js.isUndefined(value))
            .map(thumbnailFromJs)
            .orNull
        )
      )
    }

  private def thumbnailFromJs(value: js.Any): Thumbnail = {
    val dict = value.asInstanceOf[js.Dictionary[js.Any]]
    new Thumbnail(
      id = CoreProperty(util.UUID.fromString(stringField(dict, "id"))),
      name = CoreProperty(stringField(dict, "name")),
      contentType = CoreProperty(stringField(dict, "contentType")),
      data = CoreProperty(stringField(dict, "data"))
    )
  }

  private def stringField(dict: js.Dictionary[js.Any], key: String): String =
    dict.get(key).map(_.asInstanceOf[String]).getOrElse("")

  /** Same shape as `ui.core.state.Property.subscribeBidirectional`, translating at each edge
    * instead of passing the value straight through.
    */
  def subscribeBidirectional(
      jsProperty: CoreProperty[js.Any],
      media: CoreProperty[Media]
  ): CoreDisposable = {
    var syncing = false

    // The model is authoritative on initial binding. Neither conversion may
    // echo back into it, otherwise an empty control erases an existing image.
    media.set(fromJs(jsProperty.get))

    val fromMedia = media.observeWithoutInitial { value =>
      if (!syncing) {
        syncing = true
        try jsProperty.set(toJs(value))
        finally syncing = false
      }
    }
    val fromJsProp = jsProperty.observeWithoutInitial { value =>
      if (!syncing) {
        syncing = true
        try media.set(fromJs(value))
        finally syncing = false
      }
    }

    CoreDisposable {
      fromMedia.dispose()
      fromJsProp.dispose()
    }
  }
}
