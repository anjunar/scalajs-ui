import { installRuntime } from "@anjunar/scalajs-ui-core";
import { installEditorRuntime } from "./dist/fullopt/editor.js";
import { bridgeRuntime } from "./dist/fullopt/main.js";

installEditorRuntime();
installRuntime(bridgeRuntime);

export { bridgeRuntime };
