/** Keep title/summary in sync with the "/editor/basics" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/scalajs-ui-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { editorBasicsPage } from "./page.js";
import { translated } from "../../app/i18n.js";
import snippet from "./page.ts?ui-code";

export function editorBasicsDoc(): void {
  docPage(
    {
      title: "Editor",
      summary: "editor(), plugins: a model-bound Ember editor whose public Markdown value remains observable and replaceable.",
    },
    () => {
      example({ code: snippet }, () => {
        editorBasicsPage();
      });

      callout("note", () => {
        text(translated(
          "plugins picks toolbar capabilities by name; an editor without plugin configuration shows the " +
            "standard toolbar. The link and image dialogs open as @anjunar/scalajs-ui-viewport windows, so " +
            "the editor needs a viewport ancestor -- entry-client.ts/entry-server.ts already wrap the whole " +
            "app in one. onSession lends the live Ember session: typed commands such as undo run against " +
            "it, and createEditor() makes the same kind of session without any DOM."
        ));
      });
    }
  );
}
