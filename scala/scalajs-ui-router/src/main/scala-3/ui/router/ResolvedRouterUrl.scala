package ui.router

import ui.core.i18n.{I18nLocale, I18nRuntime}

private[router] final case class ResolvedRouterUrl(
    path: String,
    browserPath: String,
    search: String,
    hash: String,
    queryParams: QueryParams,
    locale: Option[I18nLocale]
) {
  def url: String =
    s"$browserPath$search$hash"

  def fragment: Option[String] =
    Option(hash.stripPrefix("#")).filter(_.nonEmpty).map(RouterConfig.decode)
}

