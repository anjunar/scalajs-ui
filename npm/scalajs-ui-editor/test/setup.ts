// jsdom does not implement window.matchMedia (https://github.com/jsdom/jsdom/issues/3522).
// EditorToolbar's ribbon layout (scalajs-ember-toolbar >= 1.0.2) queries it to decide whether
// ribbon groups collapse, so mounting the native editor throws in every test without this --
// Editor.scala's mountNative() catches that as a NonFatal failure and falls back to the plain
// Markdown source view, exactly like it would for a real, unrecoverable Ember mount error.
if (typeof window.matchMedia !== "function") {
  window.matchMedia = (query: string): MediaQueryList => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }) as MediaQueryList;
}
