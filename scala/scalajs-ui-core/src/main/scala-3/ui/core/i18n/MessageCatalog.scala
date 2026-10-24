package ui.core.i18n

final class MessageCatalog private (entries: Map[MessageFingerprint, CatalogEntry]) {
  def entryFor(key: MessageKey): Option[CatalogEntry] =
    entries.get(key.fingerprint).filter(_.key.context == key.context)

  def keys: Iterable[MessageKey] =
    entries.values.map(_.key)
}

object MessageCatalog {
  val empty: MessageCatalog =
    new MessageCatalog(Map.empty)

  def apply(entries: CatalogEntry*): MessageCatalog = {
    val indexed = entries.map(entry => entry.key.fingerprint -> entry).toMap
    require(indexed.size == entries.size, "Duplicate message fingerprints in catalog")
    new MessageCatalog(indexed)
  }
}
