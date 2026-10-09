package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js

/** `i18n-provider` -- mounts an [[I18nProviderRoot]] around a runtime built from the translated
  * catalog and locale config.
  */
private[bridge] object I18nProviderFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    // `js.Dictionary.get` cannot tell "key absent" from "key present with value `undefined`"
    // apart -- both are legitimate here (TypeScript's optional `initialUrl?`/`basePath?` produce
    // the former when omitted from the object literal, the latter when spread from a variable that
    // happens to hold `undefined`). Reading through `js.UndefOr` instead of `Option` treats both
    // the same way a native facade's own `UndefOr` field would.
    def optionalString(key: String): Option[String] =
      options.getOrElse(key, js.undefined).asInstanceOf[js.UndefOr[String]].toOption

    val catalogEntries       = options("catalog").asInstanceOf[js.Array[CatalogEntryFacade]]
    val supportedLocaleCodes = options("supportedLocales").asInstanceOf[js.Array[String]]
    val defaultLocaleCode    = options("defaultLocale").asInstanceOf[String]
    val initialUrl           = optionalString("initialUrl")
    val basePath             = optionalString("basePath").getOrElse("")

    val config = I18nConfig(
      resolver = I18nResolver(MessageCatalog(catalogEntries.toSeq.map(I18nFactories.toScala)*)),
      supportedLocales = supportedLocaleCodes.toSeq.map(I18nLocale(_)),
      defaultLocale = I18nLocale(defaultLocaleCode)
    )

    // Same default as RouterFactory.mount: an explicit `initialUrl` (SSR) wins, otherwise the
    // browser's own location -- consistent so a locale segment `Router` strips or adds agrees with
    // the locale this runtime resolved its initial `locale` property from.
    val startUrl = initialUrl.getOrElse(cursor.browserUrl.getOrElse("/"))
    val runtime  = I18nRuntime.managed(config, startUrl, basePath)

    DslLayer.child(new I18nProviderRoot(runtime, body)) {}
  }
}
