/**
 * Shared plumbing for the forms facades.
 *
 * Every form component is a registry entry in `scalajs-ui-bridge`
 * (`FormFactories.scala`). The consumer writes ambient-scope DSL callbacks --
 * `() => void`, `(index, scope) => void`, `(item, selected) => (scope) => void`
 * -- and this module turns each into the `(scope) => void` shape the bridge
 * runs against a fresh handle, the same wrap `@anjunar/scalajs-ui-controls`'s own
 * `internal.ts` applies to a cell renderer.
 */
import { scopeBody } from "@anjunar/scalajs-ui-core";
import type { ReadOnlyProperty, ScopeHandle } from "@anjunar/scalajs-ui-core";

// `defined` drops `undefined` option entries before they cross to the bridge, which reads
// top-level options by key presence (`options.get("model")`). One implementation, in core, shared
// with every other facade.
export { defined } from "@anjunar/scalajs-ui-core";

/** A body the bridge runs: it opens the ambient scope and calls the consumer's callback. */
export type ScopeBody = (scope: ScopeHandle) => void;

/** Wraps a plain `() => void` DSL body. */
export const body: (run: () => void) => ScopeBody = scopeBody;

/** Wraps an `(index) => void` array-item renderer into `(index) => ScopeBody`. */
export function indexBody(render: (index: number) => void): (index: number) => ScopeBody {
  return (index) => scopeBody(() => render(index));
}

/** Wraps an `(item, selected) => void` combo-box item renderer into its curried bridge shape. */
export function itemSelectedBody<T>(
  render: (item: T, selected: ReadOnlyProperty<boolean>) => void
): (item: T, selected: ReadOnlyProperty<boolean>) => ScopeBody {
  return (item, selected) => scopeBody(() => render(item, selected));
}
