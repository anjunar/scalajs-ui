package ui.bridge

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.i18n.*
import ui.core.render.Cursor

import scala.scalajs.js
private[bridge] object I18nFactories {

  def toScala(facade: MessageKeyFacade): MessageKey =
    MessageKey(
      source = facade.source,
      context = facade.context.toOption.map(MessageContext(_)),
      fingerprint = MessageFingerprint(facade.fingerprint),
      placeholders = facade.placeholders.toVector,
      position = facade.position.toOption.map(p => MessageSourcePosition(p.file, p.line, p.column))
    )

  def toScala(facade: RuntimeMessageFacade): RuntimeMessage =
    RuntimeMessage(
      key = toScala(facade.key),
      args = facade.args.toVector.map(arg => MessageArg(arg.name, arg.value))
    )

  def toScala(facade: CatalogEntryFacade): CatalogEntry =
    CatalogEntry(
      key = toScala(facade.key),
      value = MessageValue(
        translations = facade.translations.toMap.map { case (code, pattern) =>
          I18nLocale(code) -> LocalizedPattern(pattern)
        }
      )
    )

}
