import { installRuntime } from "@anjunar/scalajs-ui-core";
import {
  installFormsRuntime,
  parseInstant,
  parseLocalDate,
  parseLocalDateTime,
} from "./dist/fullopt/forms.js";
import { bridgeRuntime } from "./dist/fullopt/main.js";

installFormsRuntime();
installRuntime(bridgeRuntime);

export { bridgeRuntime, parseInstant, parseLocalDate, parseLocalDateTime };
