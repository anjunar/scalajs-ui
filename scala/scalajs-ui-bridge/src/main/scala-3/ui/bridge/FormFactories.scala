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

/** Step 5/6 of JAVASCRIPT_API.md §9 ("Router-Fassade, dann Forms-Schema" / "Komponentenregistratur
  * auffüllen"): the forms facade -- the trigger from CLAUDE_REVIEW_3.md §5 was
  * "`@anjunar/scalajs-ui-controls` exists AND a form-schema projection is designed" (settled: a
  * TS-native validator schema, see `npm/scalajs-ui-forms/src/validators.ts`).
  *
  * The one real design problem this file solves: `ui.forms.Form`/`SubForm` bind a control to a
  * model property by asking a macro-built `reflect.ClassDescriptor` for the accessor named
  * `control.name` -- and there is no such descriptor for a plain TypeScript object, because there
  * is no Scala case class behind it. `DynamicFormular` below is the alternative binding strategy: a
  * TS model is a plain `Record<string, Property<T> | ListProperty<T>>`, so the control's model
  * property is found by dynamic key lookup instead of macro reflection -- no `ClassDescriptor`
  * involved. This mirrors `Formular.bind`/`bindNow` almost line for line; it does not touch
  * `Formular.scala` itself, so the 285+ tests on the `ClassDescriptor` path are undisturbed.
  *
  * Validators have the same shape of problem and the same shape of answer: `ValidatorFactory` (in
  * `scalajs-ui-forms`) already dispatches on `reflect.Annotation(annotationClassName, parameters)`
  * at runtime, not at macro time -- `npm/scalajs-ui-forms/src/validators.ts` builds exactly that
  * shape as plain data (`notNull()`, `size(1, 100)`, ...), and `FormFactories.schemaFrom` turns it
  * into real `Annotation` values that the *same*, unmodified `ValidatorFactory`/`BuiltinValidators`
  * consume. No validator logic is ported to TypeScript.
  *
  * `ArrayForm`, `FieldSet`, `ComboBox`, `ImageCropper`, `Input`, `InputContainer` need none of this
  * -- they have no `ClassDescriptor` dependency at all, so their factories register the existing
  * Scala classes directly, exactly like `ControlFactories`/`ViewportFactories` do.
  *
  * What is not projected in this pass: `SubForm`'s `newInstance()`/`clearForm()`/`factory` (an
  * imperative reinstantiation hook -- the facade is reactive-input only, so a JS consumer wanting a
  * fresh instance mounts a new `subForm` under a `when()` instead), `ComboBox`'s `valueRenderer`/
  * `footerRenderer`/`identityBy`/`selectionText`/`dropdownWidth`/`dropdownHeight`/`rowHeight`, and
  * a dynamic model that swaps its whole shape after the control tree is built (binding happens
  * once, at registration -- matching `Formular`'s own per-control bind, which likewise never
  * re-resolves the accessor after the first successful bind). Each has an obvious trigger to add
  * later.
  */
private[bridge] object FormFactories {

  /** A model field, resolved dynamically by control name. `PropertyHandle`/`ListPropertyHandle` are
    * the concrete bridge classes (not the `JsReadOnlyProperty` duck type) because binding needs the
    * genuine two-way Scala `Property`/`ListProperty`, not a read-only wrapper -- the same reason
    * `ControlFactories.source` special-cases `ListPropertyHandle` instead of going through
    * `ReactiveBridge`.
    */
  def resolveModelProperty(model: js.Dictionary[js.Any], name: String): Option[Any] =
    model.get(name).collect {
      case handle: PropertyHandle[?]     => handle.underlyingProperty
      case handle: ListPropertyHandle[?] => handle.underlyingList
    }

  /** `{ fieldName: [{ name, parameters }] }` from `npm/scalajs-ui-forms/src/validators.ts` -> real
    * `Annotation`s, the same value `ValidatorFactory.createValidators` already knows how to read
    * off a macro-built `PropertyDescriptor`.
    */
  def schemaFrom(options: js.Dictionary[js.Any]): Map[String, Array[Annotation]] =
    options.get("schema") match {
      case Some(raw) =>
        raw
          .asInstanceOf[js.Dictionary[js.Array[ValidatorSpecFacade]]]
          .toMap
          .view
          .mapValues(specs => specs.map(toAnnotation).toArray)
          .toMap
      case None => Map.empty
    }

  private def toAnnotation(spec: ValidatorSpecFacade): Annotation =
    Annotation(spec.name, spec.parameters.toMap)

  /** `Int => (AbstractComponent, Cursor) ?=> Control[?]` for `ArrayForm.Renderer`, from a JS
    * `(index, scope) => void` that mounts its item through the DSL. `ArrayForm.compose` sets
    * `currentIndex` before calling this, so the item's own self-registration (`Input.compose`'s
    * `controller.register(this)`, same as any other control under a `FormController`) already lands
    * in `ArrayForm.mountedByIndex` by the time `render` returns below -- `itemControlAt` reads that
    * back instead of requiring the JS body to construct and return a `Control[?]` value, which the
    * `(scope) => void` shape the rest of this bridge uses cannot express.
    *
    * `self` is a thunk, not a value: this renderer has to exist *before* the `ArrayForm` it reads
    * from does, because `ArrayFormFactory` passes it to the constructor (`initialRenderer`) so the
    * very first `compose` sees a renderer already -- see that constructor parameter's doc comment
    * for why setting it after `compose` (`controlRenderer_=`, the ordinary DSL path) is a real
    * hydration bug, not just a style difference.
    */
  def arrayFormRenderer(
      name: String,
      self: () => ArrayForm[js.Any],
      render: js.Function1[Int, js.Function1[ScopeHandleBridge, Unit]]
  ): ArrayForm.Renderer =
    (index: Int) =>
      (parent: AbstractComponent) ?=>
        (cursor: Cursor) ?=> {
          render(index)(new ScopeHandleBridge(parent, cursor))
          self().itemControlAt(index).getOrElse {
            throw new IllegalStateException(
              s"arrayForm '$name' item renderer for index $index did not mount a control."
            )
          }
        }
}
