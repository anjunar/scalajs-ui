/**
 * Extension factories for `createEditor` (architecture §23: "Ext-Fabriken statt
 * Plugin-Stringliste").
 *
 * Each call returns an opaque handle of the linked Scala.js runtime. A handle is
 * a recipe, not an installed extension: the same handle can configure any number
 * of sessions. Options are validated when the handle is made, so a wrong option
 * throws at the call that passed it.
 */
import { editorApi } from "@anjunar/scalajs-ui-bridge/editor-api";
import { defined } from "./internal.js";

declare const extensionBrand: unique symbol;

/** An extension handle. Only the factories below make one; a look-alike object is refused. */
export interface EditorExtension {
  /** The factory that made it, for diagnostics. */
  readonly name: string;
  readonly [extensionBrand]: true;
}

export interface LinkPolicyOptions {
  /** Allowed URL schemes. Defaults to http, https, mailto and tel. */
  readonly schemes?: readonly string[];
  /** Whether relative paths are link targets. Defaults to true. */
  readonly allowRelative?: boolean;
}

export interface ImagePolicyOptions {
  /** Allowed URL schemes. Defaults to https. */
  readonly schemes?: readonly string[];
  /** Whether relative paths are image sources. Defaults to true. */
  readonly allowRelative?: boolean;
  /** Allowed hosts, compared lower case. Omitted accepts any host. */
  readonly hosts?: readonly string[];
}

function extension(handle: unknown): EditorExtension {
  return handle as EditorExtension;
}

/** Paragraphs, headings, quotes, breaks, rules and text marks. The base every session needs. */
export function richText(): EditorExtension {
  return extension(editorApi.richText());
}

/** Undo and redo. Imports (`replaceMarkdown`, `replaceJson`) reset it. */
export function history(): EditorExtension {
  return extension(editorApi.history());
}

/** Bullet and ordered lists with indent and outdent. Needs `richText()`. */
export function lists(): EditorExtension {
  return extension(editorApi.lists());
}

/** Fenced code blocks. Needs `richText()`. */
export function code(): EditorExtension {
  return extension(editorApi.code());
}

/** Inline links; every target passes the policy, in commands and in imports alike. */
export function links(options: LinkPolicyOptions = {}): EditorExtension {
  return extension(editorApi.links(defined({ ...options })));
}

/** Images; every source passes the policy, in imports alike. */
export function images(options: ImagePolicyOptions = {}): EditorExtension {
  return extension(editorApi.images(defined({ ...options })));
}
