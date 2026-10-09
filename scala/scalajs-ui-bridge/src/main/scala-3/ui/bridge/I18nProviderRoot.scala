package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js

/** The component `i18nProvider()` mounts: it owns one [[I18nRuntime]] and puts `body` under it.
  *
  * This is what `app.App.compose` assembles by hand on the Scala side --
  * `I18nRuntime.managed(...)`, then `I18nRuntime.provide(i18nRuntime)(using this)`. A TypeScript
  * user gets both from one call; everything nested inside `body` -- including a `router()`, which
  * reads `I18nRuntime.current` for its own locale-prefixed URLs
  * (`ui.router.Router.synchronizeI18n`) -- sees this runtime through the ordinary component-context
  * walk, exactly as it would on the Scala side.
  */
private[bridge] final class I18nProviderRoot(
    runtime: I18nRuntime,
    body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
) extends AbstractCustomComponent {

  override def compose(cursor: Cursor): Unit = {
    I18nRuntime.provide(runtime)(using this)

    DslLayer.render(this, cursor) {
      body(new ComponentHandleBridge(this), new ScopeHandleBridge(this, cursor))
    }
  }
}
