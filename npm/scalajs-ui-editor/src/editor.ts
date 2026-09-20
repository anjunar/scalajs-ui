/**
 * Native Ember rich-text field in the shared Scala JS UI runtime. Markdown remains
 * the form value. Link and image forms mount directly in a Viewport ancestor.
 * The ribbon provides grouped commands with keyboard navigation; menu/floating
 * use a compact toolbar. Tables edit natively, with row/column commands and a
 * draggable cell-range selection. The Markdown view preserves documents with
 * features outside the native model (raw HTML, extra text marks).
 */
import { component, type Property } from "@anjunar/scalajs-ui-core";
import { defined } from "./internal.js";
import type { EditorSession } from "./session.js";

/** The only public value representation of an editor document. */
export type Markdown = string;

/** A permanent internal media URL. No data URLs, blob URLs or external hosts. */
export interface MediaReference {
  readonly src: string;
  readonly mediaId?: string;
}

export interface ImageReference extends MediaReference {
  readonly alt: string;
  readonly title?: string;
  readonly widthPx?: number;
}

export interface UploadedMediaReference extends MediaReference {
  readonly mediaId: string;
}

/** Resolve only after the media is persisted and its internal URL is usable. */
export interface MediaUploader {
  upload(file: File, signal: AbortSignal): Promise<UploadedMediaReference>;
}

/** Must be deterministic and synchronous: also called during SSR. */
export interface MediaUrlPolicy {
  resolve(src: string): MediaReference | null;
}

export interface MediaUploadStatus {
  readonly pending: number;
  readonly error: string | null;
}

/** Toolbar capabilities of a mounted editor -- not extensions; see `createEditor` for those.
 * Empty/omitted uses the standard set.
 * base: bold, italic, inline code; table: insert a table plus row/column/table commands, shown
 * only while the caret or selection is inside one.
 * Omitted capabilities do not restrict the document schema -- tables, like every other node type,
 * always decode and render; the plugin only gates the toolbar commands that create or edit them.
 */
export type EditorPluginName =
  | "base"
  | "heading"
  | "list"
  | "link"
  | "image"
  | "table"
  | "code"
  | "horizontalRule";

export type EditorToolbarMode = "ribbon" | "menu" | "floating";

export interface EditorOptions {
  /** Used by the image plugin for picker, clipboard and dropped files. */
  readonly mediaUploader?: MediaUploader;
  /** Optional additional restrictions on internal URLs and stable ID lookup. */
  readonly mediaUrlPolicy?: MediaUrlPolicy;
  /** Upload state is separate from Markdown; use pending to govern application saves. */
  readonly onMediaStatus?: (status: MediaUploadStatus) => void;
  /** Initial Markdown for a standalone editor; a form binding takes precedence. */
  readonly value?: Markdown;
  readonly placeholder?: string;
  /** Whether the field is editable. A Property keeps external controls and the editor in sync. */
  readonly editable?: boolean | Property<boolean>;
  /** Whether to show the Markdown source textarea. A Property keeps external controls in sync. */
  readonly markdownMode?: boolean | Property<boolean>;
  /** Renders the built-in Markdown and readonly mode actions. Defaults to `true`. */
  readonly showModeActions?: boolean;
  /** Optional override for the Edit link; defaults to `?${name}.editor=editable`. */
  readonly editUrl?: string;
  /** Label for `editUrl`; defaults to `"Edit"`. */
  readonly editLabel?: string;
  /** Optional override for the Readonly link; defaults to `?${name}.editor=readonly`. */
  readonly readonlyUrl?: string;
  /** Label for `readonlyUrl`; defaults to `"Readonly"`. */
  readonly readonlyLabel?: string;
  /** Defaults to `"ribbon"`, `ui.editor.Editor`'s own default. */
  readonly toolbarMode?: EditorToolbarMode;
  /** Defaults to the standard toolbar. The table capability adds table commands. */
  readonly plugins?: readonly EditorPluginName[];
  /**
   * Receives the live session each time the visual surface mounts one -- in the browser only, and
   * again after the Markdown view hands back. The session belongs to the editor: `dispose()` ends
   * only this handle, and the handle stops working when the surface closes.
   */
  readonly onSession?: (session: EditorSession) => void;
  /** Skips registration with the enclosing form context -- an editor with no model binding. */
  readonly standalone?: boolean;
}

/** Mounts a rich-text editor field named `name`. */
export function editor(name: string, options: EditorOptions = {}): void {
  component("editor", defined({ name, ...options }));
}
