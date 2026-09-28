# scalajs-ui-editor

Rich-text editing for Scala JS UI 1.0, backed by Ember in the browser and exposed as a regular `Control[String]`. Markdown is the public value in SSR and in the browser; Ember editor state is an implementation detail.

## Overview

`scalajs-ui-editor` builds on `scalajs-ui-forms` and `scalajs-ui-viewport`. The public `Editor` component owns the Markdown value, form/control state, rendering mode, and browser adapter. Readonly SSR uses a deterministic semantic Markdown renderer; editable SSR uses a textarea that the browser progressively enhances to Ember.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ui-editor" % "1.1.0"
```

The module uses the published `scalajs-ember` libraries and the viewport for link and image dialogs.

## Quick start

```scala
import ui.editor.Editor.editor
import ui.editor.plugins.*

editor("body") {
  value = "## Article\n\nStart writing here."
  placeholder = "Write the article..."
  ribbonToolbar()
  basePlugin()
  headingPlugin()
  listPlugin()
  linkPlugin()
  imagePlugin()
}
```

## Markdown contract

The public value is CommonMark-shaped Markdown with the implemented project extensions: headings, paragraphs, block quotes, ordered and unordered lists, emphasis, strong, strike-through, highlight (`==text==`), inline code, links with optional titles, underline (`++text++`), fenced code blocks, images, horizontal rules, and GFM pipe tables with column alignment. Image width can use `![alt](url){width=320}`.

Raw HTML is rendered as text. Images require permanent internal paths beginning with a single `/`;
external, data and blob URLs are rejected. Links retain their separate HTTP(S), mailto, tel and
relative URL policy. Table captions, multiline cells, nested tables, colspan/rowspan and arbitrary
HTML are not represented.

## Media contract

The image plugin accepts `Editor.mediaUploader`, a `MediaUploader` whose
`upload(file, signal): Future[UploadedMediaReference]` completes after durable storage. The
result contains an internal `src` and a stable `mediaId`. `Editor.mediaUrlPolicy` can further
restrict paths and synchronously resolve IDs for imported Markdown. `ui.editor.MediaReference` carries an internal path and optional media ID; image alt text, title
and width belong to the document. `Editor.mediaStatusProperty` exposes pending uploads and errors.

The picker, file paste and drop share one upload coordinator. It inserts nodes after upload,
invalidates pending work when the document or lifecycle changes and retains the dialog on
failure. No FileReader/Base64 insertion path is used.
Without an uploader, existing internal images and their metadata remain usable.

`![alt](/media/4711 "title"){width=320}` preserves title and explicit width, including the former
680px default. Width is a positive integer in CSS pixels; omission means natural responsive
width. Browser and SSR share the image grammar and URL policy. Existing embedded images are
discarded on load; externally assigned invalid image content fails document validation.

Storage, backend validation, orphan cleanup and a future multipart HTML POST handler belong to
the application. The TypeScript [media integration guide](../npm/scalajs-ui-editor/README.md) documents
the complete adapter and backend contract.

The native editing engine is provided by the `scalajs-ember` 1.0.3 libraries from Maven Central.

## Editor language

Editor-owned labels use English source messages and the nearest `I18nRuntime`. Without a
runtime, the editor remains English. Add translations to your application's existing catalog
using the public `EditorMessages` keys:

```scala
import ui.core.i18n.{I18n, I18nLocale, MessageCatalog}
import ui.editor.EditorMessages

val editorTranslations = MessageCatalog(
  I18n.entry(EditorMessages.undo.key)
    .translations(I18nLocale("de") -> "Rückgängig"),
  I18n.entry(EditorMessages.apply.key)
    .translations(I18nLocale("de") -> "Übernehmen"),
  I18n.entry(EditorMessages.uploading(0).key)
    .translations(I18nLocale("de") -> "{count} Bilder werden hochgeladen")
)
```

Include these entries when constructing the catalog passed to your application's runtime, then
provide that runtime on an ancestor of `editor(...)`. Missing translations fall back to the
English source. Preserve `{count}` in upload messages and `{reason}` in Markdown fallback messages.

Changing the runtime locale updates toolbar names and tooltips, mode controls, upload counts,
and open dialog titles, fields and buttons. The native session, document, selection and undo
history remain alive; dialog input is retained. The native toolbar component is replaced because
Ember takes its labels in the constructor. Explicit `editLabel` and `readonlyLabel` strings remain
application-controlled. Messages returned by upload services or the editing engine keep their
original diagnostic text.

## SSR and non-JavaScript behavior

With `editable = false`, SSR renders semantic readonly HTML. With `editable = true`, SSR renders the Markdown source in a textarea. `editUrl` and `readonlyUrl` create ordinary links for switching modes without JavaScript; their defaults use `<editor-name>.editor=editable|readonly` while retaining the current URL scope. Hydration claims the fallback and enhances it to Ember.

## API overview

- `Editor.editor` — the public editor component.
- `Editor.value`, `placeholder`, `editable`, `standalone` — value and binding settings.
- `Editor.ribbonToolbar`, `menuToolbar`, `floatingToolbar` — toolbar modes.
- `ui.editor.plugins` — base, heading, list, link, image, table, code, and horizontal-rule plugins.
- `MarkdownRenderer` and `NativeEditorAdapter` are internal implementation helpers.

## Related modules

- [`scalajs-ui-forms`](../scalajs-ui-forms/README.md) provides the `Control[String]` binding.
- [`scalajs-ui-viewport`](../scalajs-ui-viewport/README.md) hosts link and image dialogs.
