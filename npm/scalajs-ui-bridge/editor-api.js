// The editor session API (P29), without installing anything: no runtime, no registration. The
// editor facade imports this, so it stays loadable where another runtime (the stub) is installed.
// The value comes from the same linked editor.js chunk the "./editor" subpath installs from, so a
// session and a mounted editor share one Scala.js runtime.
export { editorApi } from "./dist/fullopt/editor.js";
