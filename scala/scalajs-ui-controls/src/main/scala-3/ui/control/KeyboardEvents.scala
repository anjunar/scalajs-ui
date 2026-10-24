package ui.control

import ui.core.render.UiEvent

import scala.scalajs.js

/** Reads keyboard events through their raw JavaScript shape rather than through
  * `dom.KeyboardEvent`, so a control behaves the same under a DOM host and under a test host that
  * hands in a plain object.
  */
private[control] object KeyboardEvents {

  /** Elements that consume arrow and Home/End keys themselves. A key press that reaches a control
    * from inside one of them belongs to that element, not to the control's navigation.
    */
  private val navigationConsumers =
    "input, textarea, select, [contenteditable]:not([contenteditable=false]), [role=textbox], " +
      "[role=combobox], [role=listbox], [role=slider], [role=spinbutton], [role=menu], " +
      "[role=menubar], [role=tree], [role=grid], [role=tablist], [role=radiogroup]"

  /** The pressed key, or None when the raw event carries no string `key`. */
  def key(event: UiEvent): Option[String] =
    raw(event).map(_.selectDynamic("key")).collect {
      case value if js.typeOf(value) == "string" => value.asInstanceOf[String]
    }

  /** False for a key press that belongs to someone else: already handled, part of an IME
    * composition, or carrying Ctrl, Meta or Alt.
    */
  def isPlain(event: UiEvent): Boolean =
    raw(event).exists { keyboard =>
      !Seq("defaultPrevented", "isComposing", "ctrlKey", "metaKey", "altKey")
        .exists(flag(keyboard, _))
    }

  /** False when the key press comes from an element inside the listening element that handles arrow
    * and Home/End keys on its own (text entry, selects, ARIA widgets with a keyboard model). The
    * listening element itself always qualifies, as does a raw event without a target.
    */
  def targetAcceptsNavigation(event: UiEvent): Boolean =
    raw(event).forall { keyboard =>
      val target = keyboard.selectDynamic("target")
      if (js.isUndefined(target) || target == null) true
      else if (target eq keyboard.selectDynamic("currentTarget")) true
      else {
        val closest = target.selectDynamic("closest")
        js.typeOf(closest) != "function" ||
        target.applyDynamic("closest")(navigationConsumers) == null
      }
    }

  private def raw(event: UiEvent): Option[js.Dynamic] =
    event.raw match {
      case raw: js.Object => Some(raw.asInstanceOf[js.Dynamic])
      case _              => None
    }

  private def flag(keyboard: js.Dynamic, name: String): Boolean = {
    val value = keyboard.selectDynamic(name)
    js.typeOf(value) == "boolean" && value.asInstanceOf[Boolean]
  }
}
