# Scala.js UI

Reactive Scala.js interfaces with SSR and hydration built into the same component model. A component DSL,
synchronous reactive state, lifecycle-aware rendering, routing, typed forms, controls and browser integrations share
one runtime.

| Version | Platform | Scala | License |
| --- | --- | --- | --- |
| 1.0.9 | Scala.js | 3.3 | MIT |

Documentation: [English](https://docs.anjunar.com/en/scalajs-ui) · [Deutsch](https://docs.anjunar.com/de/scalajs-ui)
Website: [English](https://anjunar.com/en/scalajs-ui) · [Deutsch](https://anjunar.com/de/scalajs-ui)

## Installation

Every module is published separately. Enable Scala.js in `project/plugins.sbt`, then add the core and the modules
your application uses. With sbt 2, `%%` adds the Scala.js suffix itself (sbt 1: `%%%`).

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
```

```scala
enablePlugins(ScalaJSPlugin)
scalaJSUseMainModuleInitializer := true

libraryDependencies += "com.anjunar" %% "scalajs-ui-core" % "1.0.9"     // components, DSL, state, SSR
libraryDependencies += "com.anjunar" %% "scalajs-ui-router" % "1.0.9"   // routes, loaders, locale prefixes
libraryDependencies += "com.anjunar" %% "scalajs-ui-controls" % "1.0.9" // tabs, carousel, tables, grids
libraryDependencies += "com.anjunar" %% "scalajs-ui-forms" % "1.0.9"    // typed forms and inputs
libraryDependencies += "com.anjunar" %% "scalajs-ui-viewport" % "1.0.9" // windows, overlays, notifications
libraryDependencies += "com.anjunar" %% "scalajs-ui-editor" % "1.0.9"   // Markdown editor
```

## First example

A `Property` drives the text, and the event updates that same state – on a page rendered on the server, after
hydration.

```scala
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.Button.button
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.{Cursor, DomCursor}
import ui.core.state.Property
import org.scalajs.dom

final class CounterApp extends AbstractComponent {
  val tagName = "main"

  override def compose(cursor: Cursor): Unit = {
    val count = Property(0)
    vbox {
      div { text(count.map(value => s"Count: $value")) {} }
      button("Increment") { onClick(_ => count.set(count.get + 1)) }
    }
  }
}

object Main {
  def main(args: Array[String]): Unit =
    Runtime.mount(new CounterApp, DomCursor.root(dom.document.getElementById("root")))
}
```

`Runtime.mount` builds into an empty element. For SSR, `Runtime.renderToString` and `Runtime.renderToStringAsync`
render the same component with an `SsrCursor`; in the browser, `Runtime.mount` with a `HydratingCursor.root(...)`
claims that markup instead of rebuilding it.

### TypeScript

The npm packages are a typed facade over the same Scala.js runtime, not a second UI implementation.
`@anjunar/scalajs-ui-core` holds the contract and DSL, `@anjunar/scalajs-ui-bridge` installs the linked runtime.

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
```

```ts
import { button, div, mount, onClick, property, text, vbox } from "@anjunar/scalajs-ui-core";
import "@anjunar/scalajs-ui-bridge";
import "@anjunar/scalajs-ui/index.css";

function page(): void {
  const count = property(0);
  vbox(() => {
    div(() => text(count.map((value) => `Count: ${value}`)));
    button("Increment", {}, () => onClick(() => count.set(count.get + 1)));
  });
}

mount(document.getElementById("root")!, page);
```

`renderToString` renders on the server, `hydrate` claims the result in the browser, `mount` builds into an empty
element.

## The principle

**01 / Render – Server first, browser second.** Every page renders as complete HTML on the server. The browser
hydrates the same component tree instead of building it again.

**02 / Compose – One DSL for structure and behaviour.** Elements, attributes, events and children are Scala code.
There is no template language and no DOM handwork.

**03 / React – State with a lifecycle.** Properties drive the view directly. Subscriptions, timers and windows belong
to a component and end with it.

## Contents

Every page shows the building block live, explains when to use it and ends with the code.

**Interaction**
- [Actions](https://docs.anjunar.com/en/scalajs-ui/button) – buttons with their event binding next to label and context
- [Images](https://docs.anjunar.com/en/scalajs-ui/image) – native images with static or reactive sources

**Architecture**
- [Layout](https://docs.anjunar.com/en/scalajs-ui/layout) – `VBox`, `HBox` and the spatial structure of a template
- [Windows](https://docs.anjunar.com/en/scalajs-ui/window) – notifications, windows and overlays above the page
- [Viewport](https://docs.anjunar.com/en/scalajs-ui/viewport) – the central layer for notifications and windows

**Foundation**
- [Router](https://docs.anjunar.com/en/scalajs-ui/router) – paths, locale and loaders
- [i18n](https://docs.anjunar.com/en/scalajs-ui/i18n) – message-centered translations, toolbar locale meets URL locale

**Runtime**
- [Rendering](https://docs.anjunar.com/en/scalajs-ui/rendering) – SSR, hydration and shell stability
- [State](https://docs.anjunar.com/en/scalajs-ui/state) – reactive properties in plain sight

**Composition**
- [Tabs](https://docs.anjunar.com/en/scalajs-ui/tabs) – panel lifecycle and keyboard selection
- [Carousel](https://docs.anjunar.com/en/scalajs-ui/carousel) – looping slides and lifecycle-bound autoplay

**Forms**
- [Forms](https://docs.anjunar.com/en/scalajs-ui/forms) – control registration and context
- [Image cropper](https://docs.anjunar.com/en/scalajs-ui/image-cropper) – upload, crop and thumbnail binding
- [ComboBox](https://docs.anjunar.com/en/scalajs-ui/combo-box) – typed selection and stable identity

**Data**
- [Table](https://docs.anjunar.com/en/scalajs-ui/table) – reactive rows and remote ranges
- [DataGrid](https://docs.anjunar.com/en/scalajs-ui/data-grid) – virtual cards and remote ranges
- [VirtualList](https://docs.anjunar.com/en/scalajs-ui/virtual-list) – variable-height visible ranges

**Editor**
- [Editor](https://docs.anjunar.com/en/scalajs-ui/editor) – Markdown values and composable plugins

## Limits

- Scala 3 and Scala.js only. The TypeScript packages need the linked Scala.js runtime from
  `@anjunar/scalajs-ui-bridge`.
- Rendering bodies are synchronous. Asynchronous data belongs in route loaders (Scala) or `fetchInto`
  (TypeScript), so SSR can wait for it before the HTML is written.
- Server and browser must render the same tree. Values that differ between the two – hashes, clock times, random
  numbers – break hydration and must not be rendered on the first pass.
- Without JavaScript the server HTML stays readable, with links and fallback controls; writing, validation feedback,
  virtualization and richer interaction arrive with hydration.

## Modules

| Area | Scala module | TypeScript package | Responsibility |
| --- | --- | --- | --- |
| Core | [`scalajs-ui-core`](scala/scalajs-ui-core/README.md) | [`@anjunar/scalajs-ui-core`](npm/scalajs-ui-core/README.md) | Components, DSL, state, rendering, document head, i18n |
| Routing | [`scalajs-ui-router`](scala/scalajs-ui-router/README.md) | [`@anjunar/scalajs-ui-router`](npm/scalajs-ui-router/README.md) | Routes, nested outlets, links, SSR status |
| Viewport | [`scalajs-ui-viewport`](scala/scalajs-ui-viewport/README.md) | [`@anjunar/scalajs-ui-viewport`](npm/scalajs-ui-viewport/README.md) | Windows, overlays, notifications |
| Controls | [`scalajs-ui-controls`](scala/scalajs-ui-controls/README.md) | [`@anjunar/scalajs-ui-controls`](npm/scalajs-ui-controls/README.md) | Tabs, carousel, table, data grid, virtual list |
| Forms | [`scalajs-ui-forms`](scala/scalajs-ui-forms/README.md) | [`@anjunar/scalajs-ui-forms`](npm/scalajs-ui-forms/README.md) | Model binding, validation, nested forms, media |
| Editor | [`scalajs-ui-editor`](scala/scalajs-ui-editor/README.md) | [`@anjunar/scalajs-ui-editor`](npm/scalajs-ui-editor/README.md) | Markdown editor backed by Lexical |
| JSON | [`scalajs-ui-json`](scala/scalajs-ui-json/README.md) | [`@anjunar/scalajs-ui-json`](npm/scalajs-ui-json/README.md) | Explicit schema-based JSON mapping |
| WebAuthn | [`scalajs-ui-webauthn`](scala/scalajs-ui-webauthn/README.md) | [`@anjunar/scalajs-ui-webauthn`](npm/scalajs-ui-webauthn/README.md) | Browser WebAuthn and passkey ceremonies |
| Bridge | [`scalajs-ui-bridge`](scala/scalajs-ui-bridge/README.md) | [`@anjunar/scalajs-ui-bridge`](npm/scalajs-ui-bridge/README.md) | JavaScript runtime boundary and linked bundle |
| CSS | — | [`@anjunar/scalajs-ui`](npm/scalajs-ui/README.md) | Default styles for UI-rendered classes |

[`npm/README.md`](npm/README.md) describes the TypeScript package family in more detail. The runnable examples are
[`scala/scalajs-ui-demo`](scala/scalajs-ui-demo) for Scala and [`npm/scalajs-ui-demo`](npm/scalajs-ui-demo) for
TypeScript; the demos are consumers, not library modules. The product page is the private workspace
[`npm/scalajs-ui-landing`](npm/scalajs-ui-landing/README.md).

## Development

Requires a JDK, sbt 2 and Node/npm.

```bash
sbt --server "Test/testOnly *"
```

runs the complete Scala test suite. For an npm package, link the bridge first, then run the package's verification:

```bash
sbt --server "scalajs-ui-bridge/fullLinkJS"
npm run verify --workspace npm/scalajs-ui-core
```

`npm run dev` starts the Scala.js demo on `http://localhost:3000/scalajs-ui/`; its `predev` step links the demo
with `sbt --server "scalajs-ui-demo/fastLinkJS"`. After changing Scala sources, link again or keep sbt's `~` watch
running. `npm run build:landing` / `npm run preview:landing` build and serve the product page alone,
`npm run check:pages` / `npm run preview:pages` validate and serve the full site in `dist/pages` with both demos.

### Releasing

One command sets the version in the Scala build, the npm workspaces, the demos, the lockfile and the version examples
in this README; `check-version` verifies the checked-in values without changing files, as CI does:

```bash
npm run set-version -- 1.0.9
npm run check-version -- 1.0.9
```

After the release checks pass, publish the Maven artifacts and the npm packages:

```powershell
.\scripts\publish-central.ps1 -Version 1.0.9
.\scripts\publish-npm.ps1
```

## License

Scala.js UI is available under the [MIT License](LICENSE). Source, releases and issue tracking live in the
[GitHub repository](https://github.com/anjunar/scalajs-ui).
