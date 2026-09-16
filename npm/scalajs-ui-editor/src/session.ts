/**
 * Editor sessions as opaque handles of the one linked Scala.js runtime
 * (architecture §23, plan P29).
 *
 * `createEditor` makes a headless session: no DOM, usable in the browser, in
 * Node and during SSR. A mounted `editor(...)` lends its live session through
 * `onSession`. Both are the same `EditorSession` type.
 *
 * Errors come in two kinds. Misuse throws: a disposed session, a wrong payload or
 * selection shape, a value that is not a handle of this runtime. What the
 * document decides is a result value, `{ ok: false, error }`: a rejected change, a
 * source that does not import, an export that would lose content.
 */
import type { Disposable } from "@anjunar/scalajs-ui-core";
import { editorApi } from "@anjunar/scalajs-ui-bridge/editor-api";
import type { Command } from "./commands.js";
import type { Markdown } from "./editor.js";
import type { EditorExtension } from "./extensions.js";
import { defined } from "./internal.js";

declare const sessionBrand: unique symbol;

/** `{ ok: true, ...T }` or `{ ok: false, error }`. */
export type EditorResult<T extends object> =
  | ({ readonly ok: true } & { readonly [K in keyof T]: T[K] })
  | { readonly ok: false; readonly error: string };

export type DispatchResult = EditorResult<{
  /** False when no installed extension took the command; the document is unchanged then. */
  handled: boolean;
  changed: boolean;
  revision: number;
}>;

export type RevisionResult = EditorResult<{ revision: number }>;

export type MarkdownResult = EditorResult<{
  value: Markdown;
  /** What the export dropped. Only ever non-empty with `allowLoss: true`. */
  losses: readonly string[];
}>;

/** The Ember document envelope. Node payloads belong to the extensions that wrote them. */
export interface EditorJson {
  readonly format: "ember-document";
  readonly formatVersion: number;
  readonly schemaVersion: number;
  readonly root: string;
  readonly nodes: readonly unknown[];
}

export type JsonResult = EditorResult<{ value: EditorJson }>;

export type Affinity = "before" | "after";

/** A position inside a text run, in UTF-16 units. */
export interface TextPoint {
  readonly node: string;
  readonly offset: number;
  readonly affinity?: Affinity;
}

/** A position between two children of `parent`. */
export interface ChildPoint {
  readonly parent: string;
  readonly index: number;
  readonly affinity?: Affinity;
}

export type EditorPoint = TextPoint | ChildPoint;

export interface RangeSelection {
  readonly type: "range";
  readonly anchor: EditorPoint;
  readonly focus: EditorPoint;
}

export interface NodeSelection {
  readonly type: "node";
  readonly nodes: readonly string[];
}

/** A selection kind an extension registered that has no DTO yet. It cannot be passed to `select`. */
export interface OtherSelection {
  readonly type: "other";
}

export type EditorSelection = RangeSelection | NodeSelection | OtherSelection;

export interface EditorCommit {
  readonly revision: number;
  readonly documentChanged: boolean;
  readonly selectionChanged: boolean;
  readonly origin: "user" | "import" | "history" | "remote" | "system";
}

export interface ConversionOptions {
  /** Accept dropped content instead of failing. Off by default: a silent loss is invisible. */
  readonly allowLoss?: boolean;
}

/** The payload argument `dispatch` takes for a `Command<P>`: none for `void`. */
export type PayloadArguments<P> = [P] extends [void] ? [] : [payload: P];

export interface EditorSession {
  /** True for `createEditor`; false for a mounted editor's session, which `dispose` does not end. */
  readonly owned: boolean;
  readonly isDisposed: boolean;
  readonly revision: number;
  /** False without `history()`. */
  readonly canUndo: boolean;
  readonly canRedo: boolean;
  readonly selection: EditorSelection | null;

  /** Runs one command in one transaction. */
  dispatch<P>(command: Command<P>, ...payload: PayloadArguments<P>): DispatchResult;

  select(selection: RangeSelection | NodeSelection): RevisionResult;

  /** Called after every change with a plain commit description. */
  subscribe(listener: (commit: EditorCommit) => void): Disposable;

  toMarkdown(options?: ConversionOptions): MarkdownResult;

  toJson(): JsonResult;

  /** Replaces the document as an import: history resets, the caret goes to the start. */
  replaceMarkdown(source: Markdown, options?: ConversionOptions): RevisionResult;

  replaceJson(value: EditorJson, options?: ConversionOptions): RevisionResult;

  /** Idempotent. Ends this handle's subscriptions, and the session itself if it is owned. */
  dispose(): void;

  readonly [sessionBrand]: true;
}

interface EditorContent {
  /** Needs `richText()` for an empty document; the other extensions add what they know. */
  readonly extensions: readonly [EditorExtension, ...EditorExtension[]];
  /** Import the initial content even if something is dropped. */
  readonly allowLoss?: boolean;
}

/** Start empty, from Markdown, or from JSON -- not from both. */
export type CreateEditorOptions = EditorContent &
  (
    | { readonly markdown?: Markdown; readonly json?: never }
    | { readonly json: EditorJson; readonly markdown?: never }
  );

/** A headless session. Throws if the extensions do not resolve or the content does not import. */
export function createEditor(options: CreateEditorOptions): EditorSession {
  return editorApi.createEditor(defined({ ...options })) as EditorSession;
}
