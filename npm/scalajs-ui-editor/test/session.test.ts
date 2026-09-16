/**
 * The P29 session API against the linked Scala.js bridge: typed commands,
 * extension factories, validated DTOs, foreign handles, dispose, and the
 * session a mounted editor lends through `onSession`.
 *
 * Needs `sbt --server "scalajs-ui-bridge/fullLinkJS"`, like every suite here.
 */
import { cpSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { afterAll, afterEach, beforeEach, describe, expect, it } from "vitest";
import { installRuntime, mount, property, renderToString, resetRuntime, runtime } from "@anjunar/scalajs-ui-core";
import { stubRuntime } from "@anjunar/scalajs-ui-core/stub";
import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";
import { form } from "@anjunar/scalajs-ui-forms";
import { viewport } from "@anjunar/scalajs-ui-viewport";
import {
  code,
  createEditor,
  editor,
  history,
  insertParagraph,
  insertText,
  links,
  lists,
  redo,
  richText,
  setHeading,
  setLink,
  toggleCodeBlock,
  toggleList,
  toggleMark,
  undo,
  type EditorCommit,
  type EditorSession,
  type TextPoint,
} from "../src/index.js";

const disposers: (() => void)[] = [];

beforeEach(() => {
  resetRuntime();
  installRuntime(bridgeRuntime);
});

afterEach(() => {
  disposers.splice(0).reverse().forEach((dispose) => dispose());
  document.body.replaceChildren();
});

function session(markdown?: string, ...extra: ReturnType<typeof richText>[]): EditorSession {
  const created = createEditor(
    markdown === undefined
      ? { extensions: [richText(), history(), ...extra] }
      : { extensions: [richText(), history(), ...extra], markdown }
  );
  disposers.push(() => created.dispose());
  return created;
}

function markdownOf(target: EditorSession): string {
  const result = target.toMarkdown();
  if (!result.ok) throw new Error(result.error);
  return result.value;
}

function caret(target: EditorSession): TextPoint {
  const selection = target.selection;
  if (selection?.type !== "range" || !("node" in selection.anchor)) throw new Error("no text caret");
  return selection.anchor;
}

describe("a headless session", () => {
  it("starts with a caret at the start and runs typed commands", () => {
    const editorSession = session("Ada");
    expect(editorSession.owned).toBe(true);
    expect(editorSession.canUndo).toBe(false);

    const typed = editorSession.dispatch(insertText, { text: "Hello " });
    expect(typed).toMatchObject({ ok: true, handled: true, changed: true });
    expect(markdownOf(editorSession)).toBe("Hello Ada\n");

    expect(editorSession.dispatch(setHeading, { level: 2 })).toMatchObject({ ok: true, handled: true });
    expect(markdownOf(editorSession)).toBe("## Hello Ada\n");
    expect(editorSession.dispatch(setHeading, { level: null })).toMatchObject({ ok: true, handled: true });
    expect(markdownOf(editorSession)).toBe("Hello Ada\n");
  });

  it("undoes and redoes through the history extension", () => {
    const editorSession = session("Ada");
    editorSession.dispatch(insertText, { text: "Hi " });
    expect(editorSession.canUndo).toBe(true);

    expect(editorSession.dispatch(undo)).toMatchObject({ ok: true, handled: true, changed: true });
    expect(markdownOf(editorSession)).toBe("Ada\n");
    expect(editorSession.canRedo).toBe(true);
    editorSession.dispatch(redo);
    expect(markdownOf(editorSession)).toBe("Hi Ada\n");
  });

  it("reports a command no installed extension takes as not handled, and changes nothing", () => {
    const editorSession = session("Ada");
    const revision = editorSession.revision;

    expect(editorSession.dispatch(toggleList, { kind: "bullet" })).toMatchObject({
      ok: true,
      handled: false,
      changed: false,
    });
    expect(editorSession.revision).toBe(revision);
    expect(markdownOf(editorSession)).toBe("Ada\n");
  });

  it("installs lists, code and links through their factories", () => {
    const editorSession = session("Ada", lists(), code(), links());

    editorSession.dispatch(toggleList, { kind: "ordered" });
    expect(markdownOf(editorSession)).toBe("1. Ada\n");
    editorSession.dispatch(toggleList, { kind: "ordered" });
    editorSession.dispatch(toggleCodeBlock, { language: "scala" });
    expect(markdownOf(editorSession)).toBe("```scala\nAda\n```\n");
    editorSession.dispatch(toggleCodeBlock, {});

    const point = caret(editorSession);
    expect(
      editorSession.select({ type: "range", anchor: point, focus: { node: point.node, offset: 3 } })
    ).toMatchObject({ ok: true });
    editorSession.dispatch(setLink, { href: "https://example.com", title: "Example" });
    expect(markdownOf(editorSession)).toBe('[Ada](https://example.com "Example")\n');
  });

  it("notifies subscribers with plain commit DTOs until the subscription ends", () => {
    const editorSession = session("Ada");
    const commits: EditorCommit[] = [];
    const subscription = editorSession.subscribe((commit) => commits.push(commit));

    editorSession.dispatch(insertText, { text: "x" });
    expect(commits).toHaveLength(1);
    expect(commits[0]).toEqual({
      revision: editorSession.revision,
      documentChanged: true,
      selectionChanged: true,
      origin: "user",
    });

    subscription.dispose();
    editorSession.dispatch(insertText, { text: "y" });
    expect(commits).toHaveLength(1);
  });

  it("round-trips through the JSON envelope and imports replacements as imports", () => {
    const editorSession = session("# Title\n\nBody");
    const json = editorSession.toJson();
    if (!json.ok) throw new Error(json.error);
    expect(json.value.format).toBe("ember-document");

    const copy = createEditor({ extensions: [richText()], json: json.value });
    disposers.push(() => copy.dispose());
    expect(markdownOf(copy)).toBe("# Title\n\nBody\n");

    const commits: EditorCommit[] = [];
    editorSession.dispatch(insertParagraph);
    expect(editorSession.canUndo).toBe(true);
    editorSession.subscribe((commit) => commits.push(commit));
    expect(editorSession.replaceMarkdown("Replaced")).toMatchObject({ ok: true });
    expect(markdownOf(editorSession)).toBe("Replaced\n");
    expect(commits.at(-1)?.origin).toBe("import");
    expect(editorSession.canUndo).toBe(false);

    expect(editorSession.replaceJson(json.value)).toMatchObject({ ok: true });
    expect(markdownOf(editorSession)).toBe("# Title\n\nBody\n");
  });

  it("refuses a lossy export unless the loss is asked for", () => {
    const editorSession = session("Ada");
    const point = caret(editorSession);
    editorSession.select({ type: "range", anchor: point, focus: { node: point.node, offset: 3 } });
    editorSession.dispatch(toggleMark, { mark: "underline" });

    expect(editorSession.toMarkdown()).toMatchObject({ ok: false });
    const lossy = editorSession.toMarkdown({ allowLoss: true });
    expect(lossy).toMatchObject({ ok: true, value: "Ada\n" });
    expect(lossy.ok && lossy.losses.length).toBeGreaterThan(0);
  });

  it("returns what the document refuses as a result, not an exception", () => {
    const editorSession = session("Ada");
    expect(
      editorSession.select({ type: "range", anchor: { node: "missing", offset: 0 }, focus: { node: "missing", offset: 0 } })
    ).toMatchObject({ ok: false });
    expect(editorSession.replaceJson({ format: "ember-document", formatVersion: 99, schemaVersion: 1, root: "r", nodes: [] }))
      .toMatchObject({ ok: false });
    expect(markdownOf(editorSession)).toBe("Ada\n");
  });

  it("throws for a configuration that does not resolve or content that does not import", () => {
    expect(() => createEditor({ extensions: [lists()] })).toThrow(/createEditor\(\)/);
    expect(() => createEditor({ extensions: [richText(), richText()] })).toThrow(/createEditor\(\)/);
    expect(() =>
      createEditor({ extensions: [richText()], markdown: "x", json: {} as never })
    ).toThrow(/not both/);
  });
});

describe("payload validation", () => {
  it("is checked by the compiler and again at run time", () => {
    const editorSession = session("Ada");
    const revision = editorSession.revision;

    // @ts-expect-error insertText needs a payload
    expect(() => editorSession.dispatch(insertText)).toThrow(/rich-text.insert-text: expected an object/);
    // @ts-expect-error wrong field name
    expect(() => editorSession.dispatch(insertText, { txt: "x" })).toThrow(/unknown field\(s\) txt/);
    // @ts-expect-error wrong field type
    expect(() => editorSession.dispatch(insertText, { text: 1 })).toThrow(/text must be a string/);
    // @ts-expect-error undo takes no payload
    expect(() => editorSession.dispatch(undo, {})).toThrow(/takes no payload/);
    // @ts-expect-error no such mark
    expect(() => editorSession.dispatch(toggleMark, { mark: "bold" })).toThrow(/mark must be one of/);
    // @ts-expect-error headings end at 6
    expect(() => editorSession.dispatch(setHeading, { level: 7 })).toThrow(/level must be 1 to 6/);
    // @ts-expect-error no such list kind
    expect(() => editorSession.dispatch(toggleList, { kind: "unordered" })).toThrow(/kind must be/);

    expect(editorSession.revision).toBe(revision);
  });

  it("runs link targets through the session's policy", () => {
    const strict = session("Ada", links({ schemes: ["https"] }));
    expect(() => strict.dispatch(setLink, { href: "javascript:alert(1)" })).toThrow(/link.set/);
    expect(() => strict.dispatch(setLink, { href: "http://example.com" })).toThrow(/link.set/);
    // @ts-expect-error unknown option
    expect(() => links({ scheme: ["https"] })).toThrow(/links\(\): unknown field\(s\) scheme/);
  });

  it("validates selection shapes", () => {
    const editorSession = session("Ada");
    // @ts-expect-error a point needs node and offset, or parent and index
    expect(() => editorSession.select({ type: "range", anchor: { node: "x" }, focus: { node: "x", offset: 0 } }))
      .toThrow(/offset must be a non-negative integer/);
    // @ts-expect-error other selections cannot be set
    expect(() => editorSession.select({ type: "other" })).toThrow(/type must be 'range' or 'node'/);
  });
});

describe("handles", () => {
  const copies: string[] = [];
  afterAll(() => copies.forEach((directory) => rmSync(directory, { recursive: true, force: true })));

  it("refuses look-alikes of commands and extensions", () => {
    const editorSession = session("Ada");
    const fake = { name: insertText.name } as unknown as typeof insertText;
    expect(() => editorSession.dispatch(fake, { text: "x" })).toThrow(/not an editor command of this runtime/);
    expect(() => createEditor({ extensions: [{ name: "richText" } as unknown as ReturnType<typeof richText>] }))
      .toThrow(/extensions\[0\] is not an editor extension of this runtime/);
  });

  it("refuses handles of a second, separately loaded copy of the linked runtime", async () => {
    // A copy of the linker output at another path is a separate module graph: its own Scala.js
    // classes, exactly what a second bundled copy of the bridge would be in an application.
    const directory = mkdtempSync(join(tmpdir(), "scalajs-ui-second-runtime-"));
    copies.push(directory);
    const packageDirectory = join(directory, "node_modules", "second-bridge");
    mkdirSync(packageDirectory, { recursive: true });
    writeFileSync(join(packageDirectory, "package.json"), JSON.stringify({ type: "module" }));
    cpSync(resolve(process.cwd(), "../scalajs-ui-bridge/dist/fullopt"), join(packageDirectory, "fullopt"), {
      recursive: true,
    });
    const second = (await import(pathToFileURL(join(packageDirectory, "fullopt", "editor.js")).href)) as {
      editorApi: {
        commands: { insertText: typeof insertText };
        richText(): ReturnType<typeof richText>;
        createEditor(options: unknown): EditorSession;
      };
    };

    const editorSession = session("Ada");
    expect(() => editorSession.dispatch(second.editorApi.commands.insertText, { text: "x" })).toThrow(
      /not an editor command of this runtime/
    );
    expect(() => createEditor({ extensions: [second.editorApi.richText()] })).toThrow(/not an editor extension/);

    const foreignSession = second.editorApi.createEditor({ extensions: [second.editorApi.richText()] });
    expect(() => foreignSession.dispatch(insertText, { text: "x" })).toThrow(/not an editor command of this runtime/);
    foreignSession.dispose();
  }, 60_000);

  it("refuses every call on a disposed session, and disposes idempotently", () => {
    const editorSession = createEditor({ extensions: [richText()] });
    let calls = 0;
    editorSession.subscribe(() => calls++);
    editorSession.dispose();
    editorSession.dispose();

    expect(editorSession.isDisposed).toBe(true);
    expect(() => editorSession.dispatch(insertText, { text: "x" })).toThrow(/disposed/);
    expect(() => editorSession.toMarkdown()).toThrow(/disposed/);
    expect(() => editorSession.revision).toThrow(/disposed/);
    expect(calls).toBe(0);
  });

  it("works while a different runtime is installed -- the session needs none", () => {
    resetRuntime();
    installRuntime(stubRuntime);
    expect(runtime().name).not.toBe("scalajs-ui-bridge");
    const editorSession = session("Ada");
    editorSession.dispatch(insertText, { text: "Hi " });
    expect(markdownOf(editorSession)).toBe("Hi Ada\n");
  });
});

describe("a mounted editor's session", () => {
  function mounted() {
    const root = document.createElement("div");
    document.body.append(root);
    const model = { body: property("Hello there") };
    const lent: EditorSession[] = [];
    const app = mount(root, () =>
      viewport(() => form(model, {}, () => editor("body", { onSession: (borrowed) => lent.push(borrowed) })))
    );
    return { root, model, lent, app };
  }

  it("is lent once per mounted surface, and changes flow into the form value", () => {
    const f = mounted();
    disposers.push(() => f.app.dispose());
    expect(f.lent).toHaveLength(1);
    const borrowed = f.lent[0]!;
    expect(borrowed.owned).toBe(false);
    // The form's own dialect: what the form submits once the document is edited.
    expect(markdownOf(borrowed)).toBe("Hello there\n");

    expect(borrowed.replaceMarkdown("Replaced **text**")).toMatchObject({ ok: true });
    expect(f.model.body.get).toBe("Replaced **text**\n");
    borrowed.dispatch(insertText, { text: "Now " });
    expect(f.model.body.get).toBe("Now Replaced **text**\n");
    expect(f.root.querySelector(".scalajs-ui-editor__surface")!.textContent).toContain("Now Replaced");
  });

  it("ends only the handle on dispose, and dies with the surface", () => {
    const f = mounted();
    const first = f.lent[0]!;
    const seen: EditorCommit[] = [];
    first.subscribe((commit) => seen.push(commit));
    first.dispose();
    expect(first.isDisposed).toBe(true);

    f.model.body.set("From the model");
    expect(seen).toHaveLength(0);

    const toggle = f.root.querySelector<HTMLButtonElement>(".scalajs-ui-editor__markdown-actions button")!;
    toggle.click();
    toggle.click();
    expect(f.lent).toHaveLength(2);
    const second = f.lent[1]!;
    expect(markdownOf(second)).toBe("From the model\n");

    f.app.dispose();
    expect(second.isDisposed).toBe(true);
    expect(() => second.toMarkdown()).toThrow(/disposed/);
  });

  it("is not lent during SSR", async () => {
    const lent: EditorSession[] = [];
    const result = await renderToString(() =>
      viewport(() =>
        editor("body", { standalone: true, value: "## Ada", onSession: (borrowed) => lent.push(borrowed) })
      )
    );
    expect(result.html).toContain("Ada");
    expect(lent).toHaveLength(0);
  });
});
