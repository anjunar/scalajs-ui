import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installRuntime, mount, property, renderToString, resetRuntime, hydrate } from "@anjunar/scalajs-ui-core";
import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";
import { form } from "@anjunar/scalajs-ui-forms";
import { viewport } from "@anjunar/scalajs-ui-viewport";
import { editor, type EditorOptions, type UploadedMediaReference } from "../src/index.js";

const disposers: (() => void)[] = [];
beforeEach(() => {
  resetRuntime(); installRuntime(bridgeRuntime);
  Object.defineProperty(window.URL, "createObjectURL", { configurable: true, value: vi.fn(() => "blob:preview") });
  Object.defineProperty(window.URL, "revokeObjectURL", { configurable: true, value: vi.fn() });
});
afterEach(() => { disposers.splice(0).reverse().forEach(dispose => dispose()); document.body.replaceChildren(); vi.restoreAllMocks(); });

function setup(markdown = "Before", options: EditorOptions = {}) {
  const root = document.createElement("div"); document.body.append(root);
  const model = { body: property(markdown) };
  const app = mount(root, () => viewport(() => form(model, {}, () => editor("body", {
    plugins: ["base", "image"], ...options,
  }))));
  disposers.push(() => app.dispose());
  const surface = root.querySelector<HTMLElement>(".scalajs-ui-editor__surface")!;
  function selectContents(node: Node = surface, collapsed = true) {
    surface.focus();
    const range = document.createRange(); range.selectNodeContents(node);
    if (collapsed) range.collapse(false);
    window.getSelection()!.removeAllRanges(); window.getSelection()!.addRange(range);
    document.dispatchEvent(new Event("selectionchange"));
  }
  function action(id: string) {
    const button = root.querySelector<HTMLButtonElement>(`[data-command="${id}"]`)!;
    button.dispatchEvent(new MouseEvent("mousedown", { button: 0, bubbles: true, cancelable: true })); button.click();
  }
  selectContents(surface.querySelector("p") ?? surface);
  return { root, surface, model, app, action, selectContents };
}
function transfer(files: File[] = [], html = "", text = "") {
  return {
    files: Object.assign(files, { item: (i: number) => files[i] ?? null }),
    items: files.map(file => ({ kind: "file", type: file.type, getAsFile: () => file })),
    types: files.length ? ["Files", "text/html", "text/plain"] : ["text/html", "text/plain"],
    getData: (type: string) => type === "text/html" ? html : type === "text/plain" ? text : "",
    setData: vi.fn(),
  };
}
function paste(surface: HTMLElement, data: ReturnType<typeof transfer>) {
  const event = new Event("paste", { bubbles: true, cancelable: true });
  Object.defineProperty(event, "clipboardData", { value: data }); surface.dispatchEvent(event);
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(yes => { resolve = yes; });
  return { promise, resolve };
}
const file = () => new File(["image"], "cat.png", { type: "image/png" });

describe("native media and Markdown form contract", () => {
  it("preserves title and width through an unrelated real formatting command", () => {
    const f = setup('Before\n\n![Katze](/media/cat.webp "Garten"){width=680}');
    f.selectContents(f.surface.querySelector("p")!, false); f.action("bold");
    expect(f.model.body.get).toContain("**Before**");
    expect(f.model.body.get).toContain('![Katze](/media/cat.webp "Garten"){width=680}');
    expect(f.surface.querySelector("img")!.getAttribute("width")).toBe("680");
    expect(new FormData(f.root.querySelector("form")!).get("body")).toBe(f.model.body.get);
  });
  it("discards legacy Base64 from the form value without deleting surrounding text", () => {
    const f = setup("Before ![old](data:image/png;base64,YQ==) after");
    expect(f.model.body.get).toBe("Before  after"); expect(f.surface.querySelector("img")).toBeNull();
  });
  it("edits image title and width in the Viewport and supports undo/redo", () => {
    const f = setup('![Cat](/media/cat.png){width=320}');
    const image = f.surface.querySelector("img")!;
    const range = document.createRange(); range.selectNode(image);
    window.getSelection()!.removeAllRanges(); window.getSelection()!.addRange(range);
    document.dispatchEvent(new Event("selectionchange")); f.action("image");
    const dialog = f.root.querySelector<HTMLFormElement>(".scalajs-ui-editor-dialog")!;
    const inputs = dialog.querySelectorAll("input");
    expect(inputs[0]!.value).toBe("/media/cat.png");
    inputs[1]!.value = "Katze"; inputs[2]!.value = "Garden"; inputs[3]!.value = "420";
    dialog.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    expect(f.model.body.get).toContain('![Katze](/media/cat.png "Garden"){width=420}');
    f.action("undo"); expect(f.model.body.get).toContain("{width=320}");
    f.action("redo"); expect(f.model.body.get).toContain("{width=420}");
  });
  it("uploads files through the shared application service without serializing media IDs", async () => {
    const upload = vi.fn().mockResolvedValue({ src: "/media/cat.png", mediaId: "cat" });
    const onMediaStatus = vi.fn();
    const f = setup("Before", { mediaUploader: { upload }, onMediaStatus });
    paste(f.surface, transfer([file()]));
    await vi.waitFor(() => expect(f.model.body.get).toContain("![](/media/cat.png)"));
    expect(upload).toHaveBeenCalledOnce();
    expect(f.model.body.get).not.toMatch(/data:|blob:|mediaId/);
    expect(onMediaStatus).toHaveBeenLastCalledWith({ pending: 0, error: null });
  });
  it("keeps pending file order when uploads complete backwards", async () => {
    const first = deferred<UploadedMediaReference>(); const second = deferred<UploadedMediaReference>();
    const upload = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const f = setup("Before", { mediaUploader: { upload } });
    paste(f.surface, transfer([file(), file()]));
    second.resolve({ src: "/media/b.png", mediaId: "b" });
    await Promise.resolve(); expect(f.surface.querySelector("img")).toBeNull();
    first.resolve({ src: "/media/a.png", mediaId: "a" });
    await vi.waitFor(() => expect(f.surface.querySelectorAll("img")).toHaveLength(2));
    expect(f.model.body.get.indexOf("/media/a.png")).toBeLessThan(f.model.body.get.indexOf("/media/b.png"));
  });
  it("reports upload errors without changing the document", async () => {
    const status = vi.fn(); const f = setup("Before", {
      mediaUploader: { upload: vi.fn().mockRejectedValue(new Error("Storage unavailable")) }, onMediaStatus: status,
    });
    paste(f.surface, transfer([file()]));
    await vi.waitFor(() => expect(status).toHaveBeenLastCalledWith({ pending: 0, error: "Storage unavailable" }));
    expect(f.model.body.get).toBe("Before");
  });
  it("aborts late uploads after an external document replacement", async () => {
    const request = deferred<UploadedMediaReference>(); const upload = vi.fn().mockReturnValue(request.promise);
    const f = setup("Before", { mediaUploader: { upload } }); paste(f.surface, transfer([file()]));
    await vi.waitFor(() => expect(upload).toHaveBeenCalledOnce());
    const signal = upload.mock.calls[0]![1] as AbortSignal;
    f.model.body.set("Replacement"); expect(signal.aborted).toBe(true);
    request.resolve({ src: "/media/cat.png", mediaId: "cat" });
    await new Promise(resolve => setTimeout(resolve, 30));
    expect(f.model.body.get).toBe("Replacement"); expect(f.surface.querySelector("img")).toBeNull();
  });
  it("aborts pending effects on unmount", async () => {
    const upload = vi.fn().mockReturnValue(new Promise(() => {}));
    const f = setup("Before", { mediaUploader: { upload } }); paste(f.surface, transfer([file()]));
    await vi.waitFor(() => expect(upload).toHaveBeenCalledOnce());
    f.app.dispose(); expect((upload.mock.calls[0]![1] as AbortSignal).aborted).toBe(true);
  });
  it("aborts a pending upload when the application switches to readonly", async () => {
    const editable = property(true);
    const request = deferred<UploadedMediaReference>(); const upload = vi.fn().mockReturnValue(request.promise);
    const f = setup("Before", { editable, mediaUploader: { upload } });
    paste(f.surface, transfer([file()]));
    await vi.waitFor(() => expect(upload).toHaveBeenCalledOnce());
    editable.set(false); expect((upload.mock.calls[0]![1] as AbortSignal).aborted).toBe(true);
    request.resolve({ src: "/media/cat.png", mediaId: "cat" });
    await new Promise(resolve => setTimeout(resolve, 30));
    expect(f.model.body.get).toBe("Before"); expect(f.surface.querySelector("img")).toBeNull();
  });
  it("rejects an uploaded URL outside the application's policy", async () => {
    const status = vi.fn(); const f = setup("Before", {
      mediaUploader: { upload: vi.fn().mockResolvedValue({ src: "/other/cat.png", mediaId: "cat" }) },
      mediaUrlPolicy: { resolve: src => src.startsWith("/media/") ? { src } : null }, onMediaStatus: status,
    });
    paste(f.surface, transfer([file()]));
    await vi.waitFor(() => expect(status.mock.calls.at(-1)?.[0].error).toBeTruthy());
    expect(f.model.body.get).toBe("Before");
  });
  it("uses the same uploader for the native file picker", async () => {
    const upload = vi.fn().mockResolvedValue({ src: "/media/cat.png", mediaId: "cat" });
    const f = setup("Before", { mediaUploader: { upload } }); f.action("upload-image");
    const input = f.root.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, "files", { value: [file()] }); input.dispatchEvent(new Event("change"));
    await vi.waitFor(() => expect(f.model.body.get).toContain("/media/cat.png")); expect(upload).toHaveBeenCalledOnce();
  });
  it("renders and edits native tables instead of falling back to the source editor", () => {
    const markdown = "| A | B |\n| --- | --- |\n| 1 | 2 |";
    const f = setup(markdown);
    expect(f.surface.style.display).toBe("");
    const table = f.surface.querySelector("table")!;
    expect(table).not.toBeNull();
    expect(table.textContent).toContain("A");
    expect(table.textContent).toContain("1");
    expect(f.model.body.get).toBe(markdown);
  });
  it("does not start uploads in readonly mode", () => {
    const upload = vi.fn(); const f = setup("Before", { editable: false, mediaUploader: { upload } });
    paste(f.surface, transfer([file()])); expect(upload).not.toHaveBeenCalled(); expect(f.model.body.get).toBe("Before");
  });
  it("preserves textarea input entered before hydration", async () => {
    const model = { body: property("Server") };
    const build = () => viewport(() => form(model, {}, () => editor("body")));
    const rendered = await renderToString(build); const root = document.createElement("div");
    root.innerHTML = rendered.html; document.body.append(root);
    root.querySelector("textarea")!.value = "Typed before hydration";
    const app = await hydrate(root, build); disposers.push(() => app.dispose());
    expect(model.body.get).toBe("Typed before hydration");
    expect(root.querySelector(".scalajs-ui-editor__surface")!.textContent).toBe("Typed before hydration");
  });
  it("hydrates image title and width without uploading again", async () => {
    const upload = vi.fn(); const build = () => viewport(() => editor("body", {
      standalone: true, editable: false, value: '![Cat](/media/cat.png "Garden"){width=320}', mediaUploader: { upload },
    }));
    const rendered = await renderToString(build); const root = document.createElement("div");
    root.innerHTML = rendered.html; document.body.append(root);
    expect(root.querySelector("img")!.getAttribute("width")).toBe("320");
    const app = await hydrate(root, build); disposers.push(() => app.dispose());
    expect(root.querySelector(".scalajs-ui-editor__surface img")!.getAttribute("title")).toBe("Garden");
    expect(root.querySelector(".scalajs-ui-editor__surface img")!.getAttribute("width")).toBe("320");
    expect(upload).not.toHaveBeenCalled();
  });
});
