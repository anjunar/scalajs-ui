# @anjunar/scalajs-ui-editor

A native Ember rich-text field for Scala JS UI. Markdown is the public value; the session, ribbon and Viewport forms use one Scala.js UI runtime.

## Overview

The TypeScript package has two entry points into the same linked Scala.js runtime:

- `editor(name, options)` mounts a form-bound field. The Scala `ui.editor.Editor` component owns SSR, hydration, Markdown conversion, browser editing, and form binding. Link and image dialogs use the UI viewport layer.
- `createEditor({ extensions })` creates a headless Ember session -- no DOM, usable in the browser, in Node and during SSR -- driven by typed commands. A mounted editor lends its own live session of the same type through `onSession`.

## Installation

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-forms @anjunar/scalajs-ui-viewport @anjunar/scalajs-ui-editor @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
```

Import `@anjunar/scalajs-ui-bridge/editor` instead of the bare package if you don't also need router/controls -- it pulls in the `scalajs-ember` dependency (about a third of the full bridge bundle) without the rest. See that package's README.

## Quick start

```ts
import { property } from "@anjunar/scalajs-ui-core";
import { form } from "@anjunar/scalajs-ui-forms";
import { editor } from "@anjunar/scalajs-ui-editor";
import { viewport } from "@anjunar/scalajs-ui-viewport";

const model = { body: property("## Article\n\nStart writing here.") };

viewport(() => form(model, {}, () => {
  editor("body", {
    placeholder: "Write the article...",
    plugins: ["base", "heading", "list", "link", "image", "table", "code", "horizontalRule"],
  });
}));
```

## Markdown and plugins

The native view supports headings, paragraphs, quotes, lists, emphasis, strong, inline code, links, fenced code, images, horizontal rules and GFM pipe tables. The UI Markdown dialect preserves image titles and `{width=320}`. Raw HTML and extra marks (`++underline++`, `~~strike~~`, `==highlight==`) open in the Markdown source view, so unrelated edits cannot silently change their meaning.

`plugins` selects toolbar and insertion commands: `base`, `heading`, `list`, `link`, `image`, `table`, `code`, and `horizontalRule`. Without plugin configuration the standard toolbar is shown. `table` adds an insert-table command plus row/column/table commands that are enabled only with the caret inside one; a table itself always decodes and renders regardless of `plugins`. Link and image dialogs call Viewport directly. Image resizing is available as a width field in the image dialog.

## Sessions and typed commands

```ts
import {
  createEditor, richText, history, lists, links, code,
  insertText, setHeading, toggleList, undo,
} from "@anjunar/scalajs-ui-editor";

const session = createEditor({
  extensions: [richText(), history(), lists(), code(), links({ schemes: ["https"] })],
  markdown: "Ada",
});

session.dispatch(insertText, { text: "Hello " });      // payload type-checked and validated
session.dispatch(setHeading, { level: 2 });
session.dispatch(undo);                                  // commands without payload take none
const subscription = session.subscribe((commit) => console.log(commit.revision, commit.origin));

const markdown = session.toMarkdown();                   // { ok: true, value, losses } | { ok: false, error }
const json = session.toJson();                           // the Ember JSON envelope as a plain value
subscription.dispose();
session.dispose();
```

Handles are opaque values of the linked runtime. A look-alike object, or a handle of a second,
separately bundled copy of the bridge, is refused -- as is every call on a disposed session.

- **Extensions** are factories, not a name list: `richText()`, `history()`, `lists()`, `code()`,
  `links({ schemes?, allowRelative? })`, `images({ schemes?, allowRelative?, hosts? })`. Each call
  is a recipe; the same handle can configure any number of sessions. The Markdown and JSON formats of
  a session are exactly what its extensions bring.
- **Commands**: `insertText`, `insertParagraph`, `deleteBackward`, `deleteForward`, `toggleMark`,
  `setHeading`, `quote`, `unquote`, `insertThematicBreak`, `toggleList`, `indent`, `outdent`,
  `toggleCodeBlock`, `setLink`, `removeLink`, `undo`, `redo`. A command whose extension is not
  installed returns `handled: false` and changes nothing.
- **Errors**: misuse throws (wrong payload or selection shape, a foreign or disposed handle, extensions
  that do not resolve). What the document decides is a result: `{ ok: false, error }` for a rejected
  change, a source that does not import, or an export that would lose content. Exports and imports are
  strict unless `{ allowLoss: true }` is passed.
- **Selection**: `session.selection` and `session.select(...)` use node ids and UTF-16 offsets
  (`{ type: "range", anchor: { node, offset }, focus: { node, offset } }` or
  `{ type: "node", nodes }`). A new session starts with a caret at the beginning.
- **Imports**: `replaceMarkdown` and `replaceJson` replace the document as an import; history resets.

A mounted editor lends its session in the browser, once per visual surface:

```ts
let session: EditorSession | null = null;
editor("body", { onSession: (lent) => { session = lent; } });
// later: session?.dispatch(undo)
```

Changes made through it flow into the form value. `toMarkdown` there uses the form's own Markdown
dialect. `dispose()` ends only the handle; the handle stops working when the surface closes (unmount,
or switching to the Markdown view -- switching back lends a new one). `onSession` is never called
during SSR.

`plugins` stays what it is: a list of toolbar capabilities of the mounted editor, not extensions.

The session API is imported from `@anjunar/scalajs-ui-bridge/editor-api`, which installs nothing.
Importing this package therefore neither installs a runtime nor conflicts with a test runtime.

## Media uploads

Images contain permanent internal references, never embedded bytes. Configure a storage adapter
to enable the picker, image-file paste and drop:

```ts
import type { MediaUploader } from "@anjunar/scalajs-ui-editor";

const mediaUploader: MediaUploader = {
  async upload(file, signal) {
    const form = new FormData();
    form.append("image", file);
    // This example route belongs to the consuming application, not the UI library.
    const response = await fetch("/media/upload", { method: "POST", body: form, signal });
    if (!response.ok) throw new Error("Image upload failed");
    return await response.json(); // { mediaId: "4711", src: "/media/4711" }
  },
};

editor("body", {
  plugins: ["base", "image"],
  mediaUploader,
  mediaUrlPolicy: {
    resolve: src => src.startsWith("/media/")
      ? { src, mediaId: src.slice("/media/".length) }
      : null,
  },
  onMediaStatus: ({ pending, error }) => {
    // Reflect pending uploads and errors in application UI / save controls.
  },
});
```

The adapter resolves only after durable storage succeeds. Its result must include a nonempty
`mediaId` and a usable internal `src`; external, `data:` and `blob:` results are rejected.
The URL policy is synchronous, deterministic and shared with SSR. It can restrict internal media
routes and recover IDs from canonical URLs. It cannot allow external URLs. Without a policy, all
unambiguous paths starting with a single `/` are eligible. Absolute URLs, including same-origin
ones, are not the persisted representation. Keep expiring storage URLs behind a stable internal
endpoint. Static internal image references may omit `mediaId`.

Alt text, title and width describe a document occurrence, independently of the stored asset:

```md
![Katze](/media/4711 "Im Garten"){width=320}
```

Width is an optional positive integer in CSS pixels (1–100000 in the native view; larger existing values stay in source view). Explicit widths, including
680, survive export. Omission means natural width constrained by the container. Percentages,
CSS expressions and other units are not supported. The renderer uses the chosen width with
`max-width: 100%` and automatic height. Reference-style images are expanded to inline Markdown;
standard code examples are preserved. The backend must support the same `{width=N}` extension.

Clipboard HTML and the native clipboard format are validated against the selected profile.
Image files use the uploader; loaded data/blob image references are discarded. Embedded HTML
image bytes are not a transport contract of the new editor. Upload errors do not create image
nodes. Multi-file uploads retain input order. Document replacement, readonly mode and unmount
abort pending insertions; composition temporarily defers insertion.
The editor never deletes stored media on its own; orphan cleanup belongs to the backend.

There is no built-in storage service. The demo therefore exposes the missing upload configuration
instead of simulating persistence. Without an uploader, existing image metadata remains editable.

## SSR and non-JavaScript behavior

`editable: false` renders semantic readonly HTML. `editable: true` renders a Markdown textarea on the server. Pass a `Property<boolean>` to control the mode from outside the editor and keep both sides synchronized. `editUrl` and `readonlyUrl` provide ordinary mode-switch links; without overrides they use `<name>.editor=editable|readonly`. Hydration claims the fallback and enhances supported documents to Ember.

The textarea stays synchronized after rich-text changes. A future HTML-only upload handler can
use an ordinary multipart POST with a separate file field and the same Markdown/reference
contract. UI forms intercept submit in JavaScript; configure the actual action, storage and
server validation in the consuming application. Document validation rejects invalid image URLs
and widths, including values assigned externally: applications must validate before saving.
The backend must enforce the same rule for REST and no-JavaScript submissions.

Link and image forms use `Viewport.WindowConf` directly. The Lexical dialog-service hooks were removed; a Viewport ancestor supplies the application window context.

## API overview

- `editor(name, options?)`
- `Markdown` — the public string value alias.
- `EditorPluginName` — supported plugin names.
- `EditorToolbarMode` — `ribbon`, `menu`, or `floating`.
- `EditorOptions` — value, binding, SSR mode, URLs, toolbar, plugins, and `onSession`.
- `createEditor(options)`, `CreateEditorOptions`, `EditorSession` — headless sessions and the lent session type.
- `Command<P>` and the command constants listed above.
- `EditorExtension` and the factories `richText`, `history`, `lists`, `code`, `links`, `images`.
- Result and DTO types: `EditorResult`, `DispatchResult`, `MarkdownResult`, `JsonResult`, `RevisionResult`, `EditorCommit`, `EditorSelection`, `EditorPoint`, `EditorJson`.

## Related modules

- [`@anjunar/scalajs-ui-forms`](../scalajs-ui-forms/README.md) supplies model binding.
- [`@anjunar/scalajs-ui-viewport`](../scalajs-ui-viewport/README.md) supplies plugin dialogs.
