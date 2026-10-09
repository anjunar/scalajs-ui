package ui.json

import ui.core.state.{ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

import java.util.UUID
import scala.annotation.meta.field
import scala.collection.immutable.ListMap
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.reflect.Reflect
import scala.scalajs.reflect.annotation.EnableReflectiveInstantiation


@JsonType("circle")
final class Circle(
    var radius: Property[Int] = Property(0)
) extends Shape
