package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.on
import ui.core.render.Cursor
import ui.core.state.{
  CompositeDisposable,
  Disposable => CoreDisposable,
  ListProperty => CoreListProperty,
  Property => CoreProperty
}
import ui.forms.*
import ui.forms.Form.FormContext
import ui.forms.validators.{Validator, ValidatorFactory}
import org.scalajs.dom
import reflect.Annotation

import scala.collection.mutable
import scala.scalajs.js
private[bridge] object ImageCropperFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name       = ControlFactories.str(options("name"))
    val standalone = options.get("standalone").map(ControlFactories.bool).getOrElse(false)

    ImageCropper.imageCropper(name, standalone) {
      val self = summon[ImageCropper]
      options.get("placeholder").foreach(value => self.placeholder(ControlFactories.strProp(value)))
      options
        .get("aspectRatio")
        .foreach(value => ImageCropper.aspectRatio_=(ControlFactories.dbl(value))(using self))
      options
        .get("outputType")
        .foreach(value => ImageCropper.outputType_=(ControlFactories.str(value))(using self))
      options
        .get("outputQuality")
        .foreach(value => ImageCropper.outputQuality_=(ControlFactories.dbl(value))(using self))
      options
        .get("windowTitle")
        .foreach(value =>
          ImageCropper.windowTitle_=(ControlFactories.str(value))(using self, summon)
        )
    }
  }
}
