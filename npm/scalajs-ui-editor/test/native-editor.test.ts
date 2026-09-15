import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { installRuntime, mount, property, resetRuntime } from "@anjunar/scalajs-ui-core";
import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";
import { form } from "@anjunar/scalajs-ui-forms";
import { viewport } from "@anjunar/scalajs-ui-viewport";
import { editor } from "../src/index.js";

const disposers: (() => void)[] = [];
beforeEach(() => { resetRuntime(); installRuntime(bridgeRuntime); });
afterEach(() => { disposers.splice(0).reverse().forEach(f => f()); document.body.replaceChildren(); });

function setup() {
  const root = document.createElement("div");
  document.body.append(root);
  const model = { body: property("Hello world") };
  const app = mount(root, () => viewport(() => form(model, {}, () => editor("body", {
    plugins: ["base", "heading", "list", "link", "image", "code"],
  }))));
  disposers.push(() => app.dispose());
  const surface = root.querySelector<HTMLElement>(".scalajs-ui-editor__surface")!;
  function select() {
    surface.focus();
    const walker = document.createTreeWalker(surface, NodeFilter.SHOW_TEXT);
    const text = walker.nextNode()!;
    const range = document.createRange();
    range.setStart(text, 0); range.setEnd(text, 5);
    window.getSelection()!.removeAllRanges(); window.getSelection()!.addRange(range);
    document.dispatchEvent(new Event("selectionchange"));
  }
  function action(id: string) {
    const button = root.querySelector<HTMLButtonElement>(`[data-command="${id}"]`)!;
    button.dispatchEvent(new MouseEvent("mousedown", { bubbles: true, cancelable: true, button: 0 }));
    button.click();
  }
  return { root, model, surface, select, action };
}

describe("native Ember in the UI Viewport", () => {
  it("navigates across groups with End and wraps past disabled history buttons", () => {
    const f = setup(); f.select();
    const bold = f.root.querySelector<HTMLButtonElement>('[data-command="bold"]')!;
    bold.focus(); bold.dispatchEvent(new KeyboardEvent("keydown", { key: "End", bubbles: true }));
    expect(document.activeElement?.getAttribute("data-command")).toBe("code-block");
    document.activeElement!.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));
    expect(document.activeElement).toBe(bold);
    expect(f.root.querySelector<HTMLButtonElement>('[data-command="indent"]')!.disabled).toBe(true);
    expect(f.root.querySelector<HTMLButtonElement>('[data-command="unquote"]')!.disabled).toBe(true);
  });
  it("edits Markdown source and returns to the native view with the same form value", () => {
    const f = setup();
    const toggle = f.root.querySelector<HTMLButtonElement>(".scalajs-ui-editor__markdown-actions button")!;
    expect(toggle.textContent).toBe("Markdown"); toggle.click();
    const source = f.root.querySelector<HTMLTextAreaElement>("textarea")!;
    source.value = "## Source heading"; source.dispatchEvent(new Event("input", { bubbles: true }));
    expect(f.model.body.get).toBe("## Source heading");
    expect(toggle.textContent).toBe("Visuell"); toggle.click();
    expect(f.surface.querySelector("h2")!.textContent).toBe("Source heading");
    expect(toggle.textContent).toBe("Markdown");
  });
  it("formats the real form value and keeps one tab stop across ribbon groups", () => {
    const f = setup(); f.select(); f.action("bold");
    expect(f.model.body.get).toContain("**Hello**");
    expect(f.root.querySelectorAll('.ember-toolbar button[tabindex="0"]')).toHaveLength(1);
    expect(f.root.querySelector('[data-command="bold"]')?.getAttribute("aria-pressed")).toBe("true");
    expect(f.root.querySelectorAll('.ember-ribbon-group[role="group"]').length).toBeGreaterThan(2);
  });

  it("opens a Viewport window, validates the URL, and applies the saved selection", () => {
    const f = setup(); f.select(); f.action("link");
    const window = f.root.querySelector<HTMLElement>(".ui-window")!;
    expect(window).not.toBeNull();
    expect(window.querySelector("dialog")).toBeNull();
    const dialog = window.querySelector<HTMLFormElement>(".scalajs-ui-editor-dialog")!;
    const fields = dialog.querySelectorAll("input");
    fields[0]!.value = "javascript:alert(1)";
    dialog.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    expect(dialog.querySelector('[role="alert"]')!.textContent).not.toBe("");
    expect(f.model.body.get).toBe("Hello world");
    fields[0]!.value = "https://example.com";
    fields[1]!.value = "Example";
    dialog.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    expect(f.model.body.get).toContain('[Hello](https://example.com "Example")');
    expect(window.classList.contains("is-hidden")).toBe(true);
    expect(document.activeElement).toBe(f.surface);
  });

  it("cancels with Escape without changing the form value", () => {
    const f = setup(); f.select(); f.action("link");
    const input = f.root.querySelector<HTMLInputElement>(".scalajs-ui-editor-dialog input")!;
    input.value = "https://example.com";
    input.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true }));
    expect(f.model.body.get).toBe("Hello world");
    expect(f.root.querySelector(".ui-window")!.classList.contains("is-hidden")).toBe(true);
  });

  it("rejects a stale dialog after the application replaces the document", () => {
    const f = setup(); f.select(); f.action("link");
    f.model.body.set("Replacement");
    const dialog = f.root.querySelector<HTMLFormElement>(".scalajs-ui-editor-dialog")!;
    dialog.querySelector("input")!.value = "https://example.com";
    dialog.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    expect(f.model.body.get).toBe("Replacement");
    expect(dialog.querySelector('[role="alert"]')!.textContent).toContain("nicht mehr gültig");
  });
});
