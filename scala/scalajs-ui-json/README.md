# scalajs-ui-json

JSON mapping for reflected Scala models and UI properties. The mapper converts models to native JavaScript JSON values and back without a global descriptor registry.

## Overview

`scalajs-ui-json` derives reflected accessors, nested models, and default-constructor factories at compile time. `JsonMapper` is stateless; schema resolution is local to each mapping operation. An explicit `JsonSchema` remains available for models that need a custom factory or dependency graph. The mapper supports plain fields, `Property`, `ListProperty`, options, maps, Scala collections, arrays, `js.Array`, primitives, parameterized models, and polymorphic models.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ui-json" % "1.0.9"
```

## Quick start

```scala
import ui.core.state.Property
import ui.json.JsonMapper

final case class Account(
    id: Property[String] = Property(""),
    name: Property[String] = Property("")
)

val account = Account()
account.id.set("a-1")
account.name.set("Ada")

val json = JsonMapper.serialize(account)
val restored = JsonMapper.deserialize[Account](json)
```

## Usage

Nested models follow their field types automatically. Declare permitted polymorphic subtypes on the abstract class:

```scala
import ui.json.{JsonSubType, JsonSubTypes, JsonType}

@JsonSubTypes(new JsonSubType(classOf[Circle]))
sealed abstract class Shape

@JsonType("circle")
final class Circle(var radius: Int = 0) extends Shape

val shape = JsonMapper.deserialize[Shape](json)
```

`JsonProperty` changes the JSON field name and can take a model class, for example `@(JsonProperty @field)(classOf[Circle])` on a field declared as `Shape`. `JsonIgnore` controls serialization and deserialization independently. `JsonId` retains a stable identifier when dirty-payload serialization omits unchanged properties. `JsonType` supplies the `@type` discriminator for polymorphic models. Models without default constructor arguments require an explicit `JsonSchema` factory.

## Core concepts

The mapper treats `Property` and `ListProperty` values as state-bearing fields. Unchanged properties are omitted from a dirty payload, while identifiers remain available. Plain fields are mapped normally. A fully bound `TypeDescriptor` can be supplied explicitly when the caller already has one.

## API overview

- `JsonMapper` — serialize and deserialize models and arrays.
- `JsonSchema` — optional custom factories, field metadata, dependencies, and subtypes.
- `JsonProperty`, `JsonIgnore`, `JsonId`, `JsonType`, `JsonSubTypes` — mapping annotations.
- `JsonSerializer`, `JsonDeserializer`, `JsonValueCodec` — mapping internals used by the facade.

## Related modules

- [`scalajs-ui-core`](../scalajs-ui-core/README.md) provides `Property` and `ListProperty`.
- [`@anjunar/scalajs-ui-json`](../npm/scalajs-ui-json/README.md) exposes the same mapping concepts to TypeScript.
