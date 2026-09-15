// This page's client bundle only ever calls core's hydrate() plus the controls/viewport used by
// table-preview.mjs -- forms and editor (and the scalajs-ember dependency editor pulls in) run
// only server-side, in previews.mjs's SSR preview rendering. Importing the matching feature
// subpaths instead of the bare "@anjunar/scalajs-ui-bridge" (which installs every feature) keeps
// that server-only code out of what ships to the browser.
import "@anjunar/scalajs-ui-bridge/core";
import "@anjunar/scalajs-ui-bridge/controls";
import "@anjunar/scalajs-ui-bridge/viewport";
import { hydrate } from "@anjunar/scalajs-ui-core";
import { counter } from "./counter.mjs";
import { projectTable } from "./table-preview.mjs";

export async function activateCounter() {
  await hydrate(document.querySelector("#counter-root"), counter);
}

export async function activateProjectTable() {
  await hydrate(document.querySelector("#table-root"), projectTable);
}
