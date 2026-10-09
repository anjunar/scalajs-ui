package ui.forms.validators

import ui.core.i18n.{CatalogEntry, I18n, I18nLocale, i18n}

/** Optional built-in translations. Merge these into the application's catalog; its entries can override them. */
object ValidationI18n {
  private val min = 0
  private val max = 0
  private val value = 0
  private val integer = 0
  private val fraction = 0

  val german: Seq[CatalogEntry] = Seq(
    I18n.entry(i18n"Must not be null".key).translations(I18nLocale("de") -> "Dieses Feld ist erforderlich"),
    I18n.entry(i18n"Must be null".key).translations(I18nLocale("de") -> "Dieses Feld muss leer bleiben"),
    I18n.entry(i18n"Must be true".key).translations(I18nLocale("de") -> "Dieses Feld muss bestätigt werden"),
    I18n.entry(i18n"Must be false".key).translations(I18nLocale("de") -> "Dieses Feld darf nicht bestätigt werden"),
    I18n.entry(i18n"Must not be empty".key).translations(I18nLocale("de") -> "Dieses Feld darf nicht leer sein"),
    I18n.entry(i18n"Must not be blank".key).translations(I18nLocale("de") -> "Dieses Feld darf nicht leer sein"),
    I18n.entry(i18n"Has an invalid format".key).translations(I18nLocale("de") -> "Das Format ist ungültig"),
    I18n.entry(i18n"Must be a valid email address".key).translations(I18nLocale("de") -> "Bitte gib eine gültige E-Mail-Adresse ein"),
    I18n.entry(i18n"Must be positive".key).translations(I18nLocale("de") -> "Der Wert muss positiv sein"),
    I18n.entry(i18n"Must be positive or zero".key).translations(I18nLocale("de") -> "Der Wert muss positiv oder null sein"),
    I18n.entry(i18n"Must be negative".key).translations(I18nLocale("de") -> "Der Wert muss negativ sein"),
    I18n.entry(i18n"Must be negative or zero".key).translations(I18nLocale("de") -> "Der Wert muss negativ oder null sein"),
    I18n.entry(i18n"Must be in the past".key).translations(I18nLocale("de") -> "Der Wert muss in der Vergangenheit liegen"),
    I18n.entry(i18n"Must be in the past or present".key).translations(I18nLocale("de") -> "Der Wert muss in der Vergangenheit oder Gegenwart liegen"),
    I18n.entry(i18n"Must be in the future".key).translations(I18nLocale("de") -> "Der Wert muss in der Zukunft liegen"),
    I18n.entry(i18n"Must be in the future or present".key).translations(I18nLocale("de") -> "Der Wert muss in der Gegenwart oder Zukunft liegen"),
    I18n.entry(i18n"Must contain exactly $min characters/items".key).translations(I18nLocale("de") -> "Muss genau {min} Zeichen/Einträge enthalten"),
    I18n.entry(i18n"Must contain at least $min characters/items".key).translations(I18nLocale("de") -> "Muss mindestens {min} Zeichen/Einträge enthalten"),
    I18n.entry(i18n"Must contain at most $max characters/items".key).translations(I18nLocale("de") -> "Darf höchstens {max} Zeichen/Einträge enthalten"),
    I18n.entry(i18n"Must contain between $min and $max characters/items".key).translations(I18nLocale("de") -> "Muss zwischen {min} und {max} Zeichen/Einträge enthalten"),
    I18n.entry(i18n"Must be greater than or equal to $value".key).translations(I18nLocale("de") -> "Muss größer oder gleich {value} sein"),
    I18n.entry(i18n"Must be less than or equal to $value".key).translations(I18nLocale("de") -> "Muss kleiner oder gleich {value} sein"),
    I18n.entry(i18n"Must be greater than $value".key).translations(I18nLocale("de") -> "Muss größer als {value} sein"),
    I18n.entry(i18n"Must be less than $value".key).translations(I18nLocale("de") -> "Muss kleiner als {value} sein"),
    I18n.entry(i18n"At most $integer integer digits and $fraction fractional digits are allowed".key).translations(I18nLocale("de") -> "Höchstens {integer} Vorkommastellen und {fraction} Nachkommastellen sind erlaubt")
  )
}
