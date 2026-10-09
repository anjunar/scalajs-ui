# scalajs-ui-core

The core Scala.js runtime and DSL for Scala JS UI 1.0. Use it to compose DOM components, bind reactive state, render on the server, hydrate in the browser, and manage component-owned resources.

## Overview

`scalajs-ui-core` is the foundation for every other UI module. It provides the component lifecycle, render cursors, state primitives, HTML layout components, control flow, async render registration, document head, request context, and i18n primitives.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ui-core" % "1.1.5"
```

Enable the Scala.js sbt plugin in the consuming project. In this repository the module is built with Scala 3.3.8 and sbt 2; `%%` supplies the Scala.js platform suffix in this build.

## Quick start

```scala
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.{Button, Div, TextComponent, VBox}
import ui.core.state.Property

import Button.button
import Div.div
import TextComponent.text
import VBox.vbox

def counter(using ui.core.component.AbstractComponent, ui.core.render.Cursor): Unit = {
  val value = Property(0)
  vbox {
    classes = Seq("counter")
    div { text(value.map(number => s"Count: $number")) {} }
    button("Increment") { onClick(_ => value.set(value.get + 1)) }
  }
}
```

## Core concepts

`DslLayer.child` mounts a child and unmounts a partially built child if composition fails. Disposables registered on a component are released when that component leaves the tree. The DSL passes the current `AbstractComponent` and `Cursor` as Scala 3 contextual values.

`Property[T]` exposes `get`, `set`, `setAlways`, `reset`, `isDirty`, `observe`, and `observeWithoutInitial`. `ListProperty[T]` is a reactive list and a `ListDataSource`; structural changes can drive `Foreach` or virtualized controls. Property notifications are synchronous and propagation cycles are rejected.

`Condition.when` mounts its body while a boolean property is true. `Foreach` mounts one body per list item. `FetchComponent.fetch` registers asynchronous work with the render context so SSR can wait for it.

`Runtime.renderToString` renders a fragment and `Runtime.renderToStringAsync` waits for async work. `Runtime.mount` renders with the supplied cursor: use `DomCursor.root(...)` for an empty browser host and `HydratingCursor.root(...)` to claim server-rendered nodes. `Head.head`, `DocumentHead`, and `ui.core.i18n` provide document metadata and locale-aware messages.

## Messages in the DSL

Provide an `I18nRuntime` on an ancestor component, then pass messages directly.
Inside the existing `compose` / `render` block:

```scala
import ui.core.dsl.AttributeDsl.{ariaLabel, ariaLabel_=}
import ui.core.i18n.i18n
import ui.core.layout.Button.button
import ui.core.layout.Div.div
import ui.core.state.Property

val busy = Property(false)
div {
  ariaLabel = i18n"Publication status"
  button(busy.map(value => if (value) i18n"Saving" else i18n"Save")) {}
}
```

`TextValue` resolves messages through the component context. A property of
messages follows both its own updates and locale changes; the mounted DSL
component owns the subscriptions. There is no need to call `runtime.text` at
these call sites. Existing string properties pass through unchanged.

`AttributeDsl` accepts these values in `ariaLabel`, `title`, `placeholder`
and `setAttribute`. String overloads remain available, including for components
that only implement `AttributeDsl`.

## SSR and hydration

Core owns the rendering contract. SSR is readable without JavaScript; hydration adds reactive writes and event behavior. Use the same component body for both paths so the browser can claim the server structure.

## API overview

- `ui.core.component.Runtime` — mount, unmount, render, and async render entry points.
- `ui.core.state.Property` / `ListProperty` — reactive scalar and collection state.
- `ui.core.layout` — `div`, `span`, `button`, `vbox`, `hbox`, `head`, and related components.
- `ui.core.dsl` — classes, attributes, styles, properties, and events.
- `Condition`, `FetchComponent`, and `Foreach` — dynamic composition.
- `DocumentHead` and `ui.core.i18n` — head entries, catalogs, and interpolation.

## Related modules

- [`scalajs-ui-router`](../scalajs-ui-router/README.md) adds route matching and navigation.
- [`scalajs-ui-forms`](../scalajs-ui-forms/README.md) binds controls to model properties.
- [`scalajs-ui-controls`](../scalajs-ui-controls/README.md) adds higher-level collections and panels.
- [`scalajs-ui-viewport`](../scalajs-ui-viewport/README.md) adds the global UI layer.
