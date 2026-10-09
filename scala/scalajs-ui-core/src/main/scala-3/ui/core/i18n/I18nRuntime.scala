package ui.core.i18n

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Property, ReadOnlyProperty}
import ui.core.text.TextValue

import java.util.regex.Matcher

trait I18nRuntime {
  def locale: ReadOnlyProperty[I18nLocale]
  def resolver: I18nResolver
  def supportedLocales: Seq[I18nLocale]
  def defaultLocale: I18nLocale
  def setLocale(locale: I18nLocale): Unit

  def text(message: RuntimeMessage): ReadOnlyProperty[String] =
    resolver.resolve(message, locale)

  def resolveNow(message: RuntimeMessage): String =
    resolver.resolve(message, locale.get)
}

object I18nRuntime {
  private val Value: Context[I18nRuntime] =
    Context.create[I18nRuntime]("I18nRuntime")

  def apply(
      localeProperty: Property[I18nLocale],
      resolverInstance: I18nResolver,
      configuredSupportedLocales: Seq[I18nLocale] = Seq.empty,
      configuredDefaultLocale: I18nLocale = I18nLocale.En
  ): I18nRuntime =
    new I18nRuntime {
      override val locale: ReadOnlyProperty[I18nLocale] = localeProperty
      override val resolver: I18nResolver               = resolverInstance
      override val supportedLocales: Seq[I18nLocale]    =
        if (configuredSupportedLocales.nonEmpty) configuredSupportedLocales.distinct
        else Seq(configuredDefaultLocale)
      override val defaultLocale: I18nLocale           = configuredDefaultLocale
      override def setLocale(locale: I18nLocale): Unit = localeProperty.set(locale)
    }

  def managed(
      config: I18nConfig,
      initialUrl: String = "/",
      basePath: String = ""
  ): I18nRuntime = {
    val initialLocale =
      I18nUrlResolver.resolveLocale(initialUrl, config, basePath)

    val localeProperty =
      Property(initialLocale)

    new I18nRuntime {
      override val locale: ReadOnlyProperty[I18nLocale] = localeProperty
      override val resolver: I18nResolver               = config.resolver
      override val supportedLocales: Seq[I18nLocale]    = config.supportedLocales
      override val defaultLocale: I18nLocale            = config.defaultLocale
      override def setLocale(locale: I18nLocale): Unit  = localeProperty.set(locale)
    }
  }

  def provide(value: I18nRuntime)(using component: AbstractComponent): Unit =
    Value.provide(value)

  def current(using component: AbstractComponent): Option[I18nRuntime] =
    Value.inject

  def require(using component: AbstractComponent): I18nRuntime =
    current.getOrElse {
      throw new IllegalStateException("No I18nRuntime found in the current component tree.")
    }

}

