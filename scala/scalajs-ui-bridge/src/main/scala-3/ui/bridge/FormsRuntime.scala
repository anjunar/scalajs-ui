package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel
private[bridge] object FormsRuntime {
  ComponentRegistry.register("form", FormFactory)
  ComponentRegistry.register("sub-form", SubFormFactory)
  ComponentRegistry.register("input", InputFactory)
  ComponentRegistry.register("input-container", InputContainerFactory)
  ComponentRegistry.register("field-set", FieldSetFactory)
  ComponentRegistry.register("array-form", ArrayFormFactory)
  ComponentRegistry.register("combo-box", ComboBoxFactory)
  ComponentRegistry.register("image-cropper", ImageCropperFactory)

  // JavaTimeBridge's parseInstant/parseLocalDate/parseLocalDateTime (JavaTimeBridge.scala) also use
  // moduleID "forms" -- date fields are what actually calls them, so they belong in the same
  // physical file forms.js already needs.

  @JSExportTopLevel("installFormsRuntime", "forms")
  def install(): Unit = ()
}

