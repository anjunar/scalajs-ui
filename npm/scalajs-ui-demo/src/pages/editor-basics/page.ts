import { button, classes, div, onClick, property, text } from "@anjunar/scalajs-ui-core";
import { form, inputContainer, input } from "@anjunar/scalajs-ui-forms";
import { editor, undo, type EditorSession } from "@anjunar/scalajs-ui-editor";
import { translated } from "../../app/i18n.js";

const sampleMarkdown =
  "## Markdown editor\n\nThe public value stays **Markdown**.\n\n" +
  "- Grouped ribbon and native formatting\n- Direct Viewport dialogs\n\n" +
  "| Plugin | Renders as |\n| --- | --- |\n| Tables | `table` |\n\n" +
  "```scala\nval publicValue = \"Markdown\"\n```\n\n---";

export function editorBasicsPage(): void {
  const model = {
    title: property(translated("Getting started").get),
    body: property(sampleMarkdown),
  };
  const editable = property(true);
  const markdownMode = property(false);
  // Lent by the mounted editor in the browser; stays null during SSR.
  let session: EditorSession | null = null;

  div(() => {
    classes("flex", "flex-col", "gap-4");
    form(model, {}, () => {
      div(() => {
        classes("flex", "flex-col", "gap-3");
        inputContainer({ label: translated("Title").get }, () => {
          input("title");
        });
        div(() => {
          classes("editor-demo__surface");
          editor("body", {
            placeholder: translated("Write the article...").get,
            editable,
            markdownMode,
            showModeActions: false,
            plugins: ["base", "heading", "list", "link", "image", "table", "code", "horizontalRule"],
            onSession: (lent) => {
              session = lent;
            },
          });
        });
      });
    });

    div(() => {
      classes("showcase-action-row");
      button(translated("Readonly"), {}, () =>
        onClick(() => editable.set(!editable.get))
      );
      button(translated("Markdown"), {}, () =>
        onClick(() => markdownMode.set(!markdownMode.get))
      );
      button(translated("Load article"), {}, () => onClick(() => model.body.set(sampleMarkdown)));
      button(translated("Clear editor"), {}, () => onClick(() => model.body.set("")));
      button(translated("Undo last change"), {}, () => onClick(() => session?.dispatch(undo)));
    });

    div(() => {
      classes("showcase-result");
      text(model.body.map((markdown) => `${translated("Markdown value").get}: ${markdown.length} ${translated("characters").get}`));
    });
  });
}
