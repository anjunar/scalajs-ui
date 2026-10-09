package ui.bridge

import ember.editor.code.{CodeCommands, CodeInfo, CodeLanguage}
import ember.editor.core.{DispatchOutcome, EditorCommand, EditorSession, UpdateError}
import ember.editor.history.HistoryCommands
import ember.editor.link.{LinkCommands, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListCommands, ListKind}
import ember.editor.richtext.{HeadingLevel, RichText, StandardMarks}

import scala.scalajs.js

/** Validation of JavaScript values at the editor boundary.
  *
  * Every failure is an `IllegalArgumentException` naming the command or factory, thrown before any
  * session is touched -- the same kind the other bridge factories throw for a misuse.
  */
private[bridge] object EditorPayloads {

  def invalid(where: String, message: String): Nothing =
    throw new IllegalArgumentException(s"$where: $message")

  private def isPlainObject(value: js.Any): Boolean =
    value != null && js.typeOf(value) == "object" && !js.Array.isArray(value)

  /** A command without a payload accepts nothing, not even `{}` -- a payload there is a mistake. */
  def none(payload: js.Any, where: String): Unit =
    if (!js.isUndefined(payload)) invalid(where, "takes no payload")

  /** A payload object with no keys other than `allowed`. */
  def fields(payload: js.Any, where: String, allowed: Set[String]): js.Dictionary[js.Any] = {
    if (!isPlainObject(payload)) invalid(where, "expected an object")
    val dictionary = payload.asInstanceOf[js.Dictionary[js.Any]]
    val unknown    = dictionary.keys.filterNot(allowed).toVector.sorted
    if (unknown.nonEmpty) invalid(where, s"unknown field(s) ${unknown.mkString(", ")}")
    dictionary
  }

  /** An options object with no keys other than `allowed`; `undefined` means none. */
  def options(value: js.Any, where: String, allowed: Set[String]): js.Dictionary[js.Any] =
    if (js.isUndefined(value)) js.Dictionary.empty
    else fields(value, where, allowed)

  def string(fields: js.Dictionary[js.Any], key: String, where: String): String =
    optionalString(fields, key, where).getOrElse(invalid(where, s"$key must be a string"))

  def optionalString(fields: js.Dictionary[js.Any], key: String, where: String): Option[String] =
    fields.get(key).filterNot(js.isUndefined).map { value =>
      if (js.typeOf(value) == "string") value.asInstanceOf[String]
      else invalid(where, s"$key must be a string")
    }

  def optionalBoolean(fields: js.Dictionary[js.Any], key: String, where: String): Option[Boolean] =
    fields.get(key).filterNot(js.isUndefined).map { value =>
      if (js.typeOf(value) == "boolean") value.asInstanceOf[Boolean]
      else invalid(where, s"$key must be a boolean")
    }

  def optionalStrings(
      fields: js.Dictionary[js.Any],
      key: String,
      where: String
  ): Option[Vector[String]] =
    fields.get(key).filterNot(js.isUndefined).map { value =>
      if (!js.Array.isArray(value)) invalid(where, s"$key must be an array of strings")
      value.asInstanceOf[js.Array[js.Any]].toVector.map { entry =>
        if (js.typeOf(entry) == "string") entry.asInstanceOf[String]
        else invalid(where, s"$key must be an array of strings")
      }
    }
}
