/**
 * Typed editor commands for `EditorSession.dispatch`.
 *
 * A command is identified by the object, never by its name: `name` is for
 * diagnostics only, and a plain object carrying the same name is refused
 * (architecture §23). The payload type is checked by the compiler and validated
 * again by the bridge, so a cast cannot smuggle a wrong payload into a session.
 */
import { editorApi } from "@anjunar/scalajs-ui-bridge/editor-api";

declare const payloadType: unique symbol;

/** A command taking payload `P`; `void` for a command without one. */
export interface Command<P> {
  /** Ember's diagnostic command name. Not a dispatch key. */
  readonly name: string;
  readonly [payloadType]: (payload: P) => void;
}

export type TextMark = "strong" | "emphasis" | "underline" | "strike" | "code";

export type HeadingLevel = 1 | 2 | 3 | 4 | 5 | 6;

export type ListKind = "bullet" | "ordered";

function command<P>(handle: unknown): Command<P> {
  return handle as Command<P>;
}

const commands = editorApi.commands;

/** Types text at the selection, replacing a range. */
export const insertText: Command<{ readonly text: string }> = command(commands.insertText);

/** Splits the block at the caret, like Enter. */
export const insertParagraph: Command<void> = command(commands.insertParagraph);

export const deleteBackward: Command<void> = command(commands.deleteBackward);

export const deleteForward: Command<void> = command(commands.deleteForward);

/** Adds the mark to the selection, or removes it where the whole selection has it. */
export const toggleMark: Command<{ readonly mark: TextMark }> = command(commands.toggleMark);

/** A heading of that level, or a paragraph for `null`. */
export const setHeading: Command<{ readonly level: HeadingLevel | null }> = command(commands.setHeading);

export const quote: Command<void> = command(commands.quote);

export const unquote: Command<void> = command(commands.unquote);

/** A horizontal rule after the current block. */
export const insertThematicBreak: Command<void> = command(commands.insertThematicBreak);

/** Needs `lists()`; without it the command is not handled. */
export const toggleList: Command<{ readonly kind: ListKind }> = command(commands.toggleList);

export const indent: Command<void> = command(commands.indent);

export const outdent: Command<void> = command(commands.outdent);

/** Needs `code()`. Turning a block off ignores `language` and `meta`. */
export const toggleCodeBlock: Command<{ readonly language?: string; readonly meta?: string }> = command(
  commands.toggleCodeBlock
);

/** Needs `links()`. `href` passes the session's link policy, or the payload is refused. */
export const setLink: Command<{ readonly href: string; readonly title?: string }> = command(commands.setLink);

export const removeLink: Command<void> = command(commands.removeLink);

/** Needs `history()`. */
export const undo: Command<void> = command(commands.undo);

/** Needs `history()`. */
export const redo: Command<void> = command(commands.redo);
