/**
 * The untyped surface of the linked editor session API.
 *
 * Deliberately loose: payloads, options and handles are `unknown` here, because the bridge validates
 * every one of them at run time. The typed API -- `Command<P>`, `EditorSession`, the extension
 * factories -- lives in `@anjunar/scalajs-ui-editor`, which is what applications import.
 *
 * Importing this subpath installs nothing.
 */
export interface EditorApi {
  createEditor(options: unknown): unknown;
  richText(): unknown;
  history(): unknown;
  lists(): unknown;
  code(): unknown;
  links(options?: unknown): unknown;
  images(options?: unknown): unknown;
  readonly commands: Readonly<Record<
    | "insertText"
    | "insertParagraph"
    | "deleteBackward"
    | "deleteForward"
    | "toggleMark"
    | "setHeading"
    | "quote"
    | "unquote"
    | "insertThematicBreak"
    | "toggleList"
    | "indent"
    | "outdent"
    | "toggleCodeBlock"
    | "setLink"
    | "removeLink"
    | "undo"
    | "redo",
    unknown
  >>;
}

export declare const editorApi: EditorApi;
